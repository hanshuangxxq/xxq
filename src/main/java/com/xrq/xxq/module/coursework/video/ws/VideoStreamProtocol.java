package com.xrq.xxq.module.coursework.video.ws;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 视频流二进制数据帧编解码：12 字节头 {@code [req:int32 BE][offset:int64 BE]} + payload。
 * 一次 read 请求恰好对应一帧；尾部短读由帧长度自然表达（payload 长度 = 帧长 - 12）。
 * 文本控制帧（meta/error）走 JSON，不在本编解码范围内。
 */
public final class VideoStreamProtocol {

    public static final int HEADER_BYTES = 12;

    private VideoStreamProtocol() {
    }

    /** 编码一帧数据。 */
    public static byte[] encodeDataFrame(int req, long offset, byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES + payload.length).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(req);
        buf.putLong(offset);
        buf.put(payload);
        return buf.array();
    }

    /** 解码（测试与前端参考实现用）。 */
    public static DataFrame decodeDataFrame(byte[] frame) {
        if (frame == null || frame.length < HEADER_BYTES) {
            throw new IllegalArgumentException("帧长度不足");
        }
        ByteBuffer buf = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN);
        int req = buf.getInt();
        long offset = buf.getLong();
        byte[] payload = new byte[frame.length - HEADER_BYTES];
        buf.get(payload);
        return new DataFrame(req, offset, payload);
    }

    /** 解码结果。 */
    public record DataFrame(int req, long offset, byte[] payload) {
    }
}
