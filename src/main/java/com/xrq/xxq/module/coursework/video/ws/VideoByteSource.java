package com.xrq.xxq.module.coursework.video.ws;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.xrq.xxq.module.coursework.video.config.VideoStreamProperties;

/**
 * 视频字节源：按 (offset, length) 读文件区间；头/尾段（moov atom 必居其一）走有界 LRU 缓存。
 * 内容寻址产物字节永不变，缓存不设过期；文件被回收删除时读抛 IOException，由调用方转 error 帧。
 * <p>
 * 显式构造器而非 Lombok：cache 的容量上限来自 props，字段初始化器阶段 props 尚未赋值（NPE 陷阱）。
 */
@Component
public class VideoByteSource {

    private final VideoStreamProperties props;
    private final Map<String, byte[]> cache;

    public VideoByteSource(VideoStreamProperties props) {
        this.props = props;
        int maxEntries = Math.max(1, props.getHeaderCacheEntries()) * 2; // 每视频 head+tail 两条
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                return size() > maxEntries;
            }
        });
    }

    /** 读 [offset, offset+length)，越界截断到文件尾；offset 越界返回空数组。 */
    public byte[] read(Path file, long fileSize, long offset, long length) throws IOException {
        if (offset < 0 || length <= 0) {
            throw new IllegalArgumentException("非法的读取区间");
        }
        if (offset >= fileSize) {
            return new byte[0];
        }
        int len = (int) Math.min(length, fileSize - offset);
        String key = cacheKey(file, offset, fileSize, len);
        if (key != null) {
            byte[] hit = cache.get(key);
            if (hit != null) {
                return hit;
            }
        }
        byte[] buf = new byte[len];
        int readBytes;
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            ch.position(offset);
            ByteBuffer bb = ByteBuffer.wrap(buf);
            while (bb.hasRemaining() && ch.read(bb) != -1) {
                // 读满为止
            }
            readBytes = bb.position();
        }
        // 磁盘上的文件比元数据里的 sizeBytes 短（DB 与磁盘不一致）时，按实际读到的长度返回。
        // 不能把补零的尾巴当数据发出去：客户端是按 payload 实际长度推进的，短读才是协议允许的表达。
        if (readBytes < len) {
            buf = Arrays.copyOf(buf, readBytes);
        }
        if (key != null) {
            cache.put(key, buf);
        }
        return buf;
    }

    /**
     * 仅头段（offset=0，长度不超过窗口）与尾段（读到文件尾且起点落在窗口内）可缓存；其余返回 null 不缓存。
     * <p>
     * 键必须带「偏移 + 长度」：缓存的是**这一段字节**，不是「头/尾」这个概念本身。
     * 曾经只用 {@code "#tail"} 作键，于是先到的短尾段（例如顺序读到最后只剩 319105 字节）会把键占住，
     * 之后请求整个 512KB 尾窗口的调用会拿到这段更短、覆盖区间也对不上的数据 —— 客户端据此
     * 永远拼不完整的 moov，播放器就一直缓冲（且只在「同一进程里先读过短尾段」后才复现）。
     */
    private String cacheKey(Path file, long offset, long fileSize, int len) {
        int window = props.getHeaderCacheBytes();
        boolean head = offset == 0 && len <= window;
        boolean tail = offset + len >= fileSize && fileSize - offset <= window;
        return head || tail ? file + "#" + offset + "+" + len : null;
    }
}
