package com.xrq.xxq.module.file.dto;

/**
 * 分片落盘结果。
 *
 * @param index         本次提交的分片序号
 * @param receivedCount 服务端当前已收分片数（Redis {@code SCARD}，O(1)，不扫磁盘目录）
 */
public record ChunkSavedView(int index, int receivedCount) {
}
