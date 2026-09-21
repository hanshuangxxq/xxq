package com.xrq.xxq.module.coursework.video.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 视频 WS 文本控制指令（客户端 → 服务端）。
 * <p>
 * 用带类型的 record 而非 JsonNode 直接取值：字段名拼写/类型错误在解析期即暴露，
 * 且未知字段被忽略（协议前瞻：新增字段不会打挂老客户端）。
 *
 * @param action open / read / cancel / progress
 * @param req    客户端自增请求号，read 的数据帧原样回带（数据帧头 4 字节）
 * @param offset 起始字节偏移（read）
 * @param length 期望字节数（read）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VideoCommand(String action, Integer req, Long offset, Long length) {
}
