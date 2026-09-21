package com.xrq.xxq.module.coursework.video.ws;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            ch.position(offset);
            ByteBuffer bb = ByteBuffer.wrap(buf);
            while (bb.hasRemaining() && ch.read(bb) != -1) {
                // 读满为止
            }
        }
        if (key != null) {
            cache.put(key, buf);
        }
        return buf;
    }

    /** 仅头段（offset=0）与尾段（落入 headerCacheBytes 窗口）可缓存；其余返回 null 不缓存。 */
    private String cacheKey(Path file, long offset, long fileSize, int len) {
        int window = props.getHeaderCacheBytes();
        if (offset == 0 && len <= window) {
            return file + "#head";
        }
        if (offset + len >= fileSize && fileSize - offset <= window) {
            return file + "#tail";
        }
        return null;
    }
}
