package com.xrq.xxq.module.coursework.video.ws;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.xrq.xxq.module.coursework.video.config.VideoStreamProperties;
import com.xrq.xxq.module.coursework.video.entity.CourseVideo;
import com.xrq.xxq.module.coursework.video.mapper.CourseVideoMapper;
import com.xrq.xxq.module.file.service.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 视频 WS 流式播放：服务端是「哑的字节区间服务器」——不转码、不解析 MP4，
 * 按 open/read/cancel 指令回 {@code [req:int32][offset:int64]+payload} 二进制帧。
 * <p>
 * 并发约定：WebSocketSession 发送非线程安全，所有 sendMessage 经 {@code st.sendLock} 串行；
 * 锁顺序恒为 {@code synchronized(st)} 外层 → {@code synchronized(st.sendLock)} 内层，禁止反向；
 * 读文件在 st 锁外进行（IO 不阻塞状态操作）。
 * <p>
 * 线程模型：drain 任务跑在<b>虚拟线程</b>上——每个连接一个 drain 循环，内部是阻塞的文件读与
 * WS 发送，用固定小线程池会把所有连接串行化（200 连接挤 2 个线程）。scheduler 只负责
 * 「限速窗口超额后的延迟续排」，本身不执行 IO。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoStreamWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_STATE = "videoStreamState";
    private static final String ATTR_PATH = "videoPath";
    private static final String ATTR_SIZE = "videoSize";

    private final ObjectMapper objectMapper;
    private final VideoStreamProperties props;
    private final VideoByteSource byteSource;
    private final CourseVideoMapper videoMapper;
    private final FileStorageService fileStorage;

    private final AtomicInteger connections = new AtomicInteger();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService drainExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
        drainExecutor.shutdownNow();
    }

    /** 每连接状态；字段只准在 synchronized(state) 下读写。 */
    private static final class SessionState {
        final Object sendLock = new Object();
        final Set<Integer> inflight = new HashSet<>();
        final ArrayDeque<ReadTask> queue = new ArrayDeque<>();
        boolean draining;
        long windowStartMs;
        long bytesInWindow;
    }

    private record ReadTask(int req, long offset, int length) {
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        if (connections.incrementAndGet() > props.getMaxConnections()) {
            connections.decrementAndGet();
            session.close(CloseStatus.SERVICE_OVERLOAD);
            return;
        }
        session.getAttributes().put(ATTR_STATE, new SessionState());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (session.getAttributes().remove(ATTR_STATE) != null) {
            // 只有真正登记过状态（未被过载拒绝）的连接才计数，否则会多减导致计数漂移
            connections.decrementAndGet();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Integer req = null;
        try {
            VideoCommand cmd = objectMapper.readValue(message.getPayload(), VideoCommand.class);
            req = cmd.req();
            String action = cmd.action() == null ? "" : cmd.action();
            switch (action) {
                case "open" -> handleOpen(session);
                case "read" -> handleRead(session, cmd);
                case "cancel" -> handleCancel(session, cmd);
                case "progress" -> {
                    // reserved：观看进度上报（本期忽略，协议前瞻兼容）
                }
                default -> sendError(session, req, 400, "未知指令: " + action);
            }
        } catch (Exception e) {
            log.warn("视频流指令处理失败: {}", e.toString());
            sendError(session, req, 400, "指令格式错误");
        }
    }

    private void handleOpen(WebSocketSession session) {
        Long videoId = (Long) session.getAttributes().get(VideoStreamHandshakeInterceptor.ATTR_VIDEO_ID);
        CourseVideo v = videoId == null ? null : videoMapper.selectById(videoId);
        if (v == null) {
            sendError(session, null, 404, "视频不存在");
            closeQuietly(session);
            return;
        }
        Path path;
        try {
            path = fileStorage.resolve(v.getFileName());
        } catch (Exception e) {
            sendError(session, null, 404, "视频文件不存在");
            closeQuietly(session);
            return;
        }
        session.getAttributes().put(ATTR_PATH, path);
        session.getAttributes().put(ATTR_SIZE, v.getSizeBytes());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", "meta");
        meta.put("videoId", v.getId());
        meta.put("size", v.getSizeBytes());
        meta.put("sha256", v.getSha256());
        if (v.getDurationSec() != null) {
            meta.put("durationSec", v.getDurationSec());
        }
        sendJson(session, meta);
    }

    private void handleRead(WebSocketSession session, VideoCommand cmd) {
        Integer reqBoxed = cmd.req();
        int req = reqBoxed == null ? -1 : reqBoxed;
        long offset = cmd.offset() == null ? -1 : cmd.offset();
        long length = cmd.length() == null ? -1 : cmd.length();
        if (req < 0 || offset < 0 || length <= 0 || length > props.getChunkMaxBytes()) {
            sendError(session, req < 0 ? null : req, 400, "非法的 read 参数");
            return;
        }
        SessionState st = state(session);
        if (st == null) {
            return;
        }
        if (session.getAttributes().get(ATTR_PATH) == null) {
            sendError(session, req, 400, "请先 open");
            return;
        }
        synchronized (st) {
            if (st.inflight.size() >= props.getMaxInflight()) {
                sendJson(session, errorPayload(req, 429, "在途请求过多，请减速"));
                return;
            }
            st.inflight.add(req);
            st.queue.add(new ReadTask(req, offset, (int) length));
            if (!st.draining) {
                st.draining = true;
                drainExecutor.execute(() -> drain(session));
            }
        }
    }

    private void handleCancel(WebSocketSession session, VideoCommand cmd) {
        int req = cmd.req() == null ? -1 : cmd.req();
        SessionState st = state(session);
        if (st == null) {
            return;
        }
        synchronized (st) {
            st.queue.removeIf(t -> t.req() == req);
        }
    }

    /** 发送循环：每连接串行；限速窗口超额则延迟 100ms 续排（draining 保持 true）。 */
    private void drain(WebSocketSession session) {
        SessionState st = state(session);
        if (st == null) {
            return;
        }
        while (session.isOpen()) {
            ReadTask task;
            synchronized (st) {
                task = st.queue.poll();
                if (task == null) {
                    st.draining = false;
                    return;
                }
                long nowMs = System.currentTimeMillis();
                if (nowMs - st.windowStartMs >= 1000) {
                    st.windowStartMs = nowMs;
                    st.bytesInWindow = 0;
                }
                if (st.bytesInWindow >= props.getRateLimitMbps() * 1024L * 1024L) {
                    st.queue.addFirst(task);
                    // 延迟续排：draining 保持 true，避免与新的 read 抢跑导致重复 drain 循环
                    scheduler.schedule(() -> drainExecutor.execute(() -> drain(session)),
                            100, TimeUnit.MILLISECONDS);
                    return;
                }
            }
            try {
                Path path = (Path) session.getAttributes().get(ATTR_PATH);
                Long sizeBoxed = (Long) session.getAttributes().get(ATTR_SIZE);
                if (path == null || sizeBoxed == null) {
                    // 连接未 open（或状态被清理）：丢弃并释放名额
                    synchronized (st) {
                        st.inflight.remove(task.req());
                    }
                    continue;
                }
                byte[] payload = byteSource.read(path, sizeBoxed, task.offset(), task.length());
                byte[] frame = VideoStreamProtocol.encodeDataFrame(task.req(), task.offset(), payload);
                synchronized (st.sendLock) {
                    session.sendMessage(new BinaryMessage(ByteBuffer.wrap(frame)));
                }
                synchronized (st) {
                    st.bytesInWindow += frame.length;
                    st.inflight.remove(task.req());
                }
            } catch (NoSuchFileException e) {
                // 播放中文件被回收删除：协议约定的 error + close
                synchronized (st) {
                    st.inflight.remove(task.req());
                }
                sendError(session, task.req(), 404, "视频文件已删除");
                closeQuietly(session);
                return;
            } catch (Exception e) {
                synchronized (st) {
                    st.inflight.remove(task.req());
                }
                log.warn("视频读取/发送失败: {}", e.toString());
                sendError(session, task.req(), 500, "读取视频失败");
            }
        }
    }

    private SessionState state(WebSocketSession session) {
        return (SessionState) session.getAttributes().get(ATTR_STATE);
    }

    private Map<String, Object> errorPayload(Integer req, int code, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("type", "error");
        if (req != null) {
            err.put("req", req);
        }
        err.put("code", code);
        err.put("message", message);
        return err;
    }

    private void sendError(WebSocketSession session, Integer req, int code, String message) {
        sendJson(session, errorPayload(req, code, message));
    }

    /** 所有发送的统一出口：经 sendLock 串行。可在 st 锁内调用（锁顺序 st → sendLock）。 */
    private void sendJson(WebSocketSession session, Map<String, Object> payload) {
        SessionState st = state(session);
        if (st == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (st.sendLock) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            log.warn("视频流控制帧发送失败: {}", e.toString());
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException e) {
            log.debug("关闭视频 WS 会话失败: {}", e.toString());
        }
    }
}
