package com.xrq.xxq.module.file.dto;

import java.util.List;

/**
 * 上传会话视图。
 * <p>
 * <b>{@code completedFile} 非空是唯一的「无需再传分片」信号</b>（秒传命中、或该会话已合并过的幂等返回）——
 * 客户端见到它即可直接跳到业务提交。此时 {@code uploadId} 可能为 {@code null}（秒传未创建会话）。
 *
 * @param uploadId       会话标识；{@code null} 表示秒传命中、无会话
 * @param biz            业务目录 code
 * @param originalName   展示名（以会话首次 init 时的值为准）
 * @param totalSize      文件总字节数
 * @param chunkSize      分片大小（服务端按 {@code ceil(totalSize/totalChunks)} 推导，客户端以此为准）
 * @param totalChunks    分片总数
 * @param sha256         整文件 SHA-256
 * @param receivedChunks 已收分片序号（升序），客户端跳过这些片即可续传
 * @param receivedCount  已收分片数
 * @param completedFile  产物引用；非空表示已完成
 */
public record UploadSessionView(String uploadId, String biz, String originalName, long totalSize,
                                long chunkSize, int totalChunks, String sha256,
                                List<Integer> receivedChunks, int receivedCount,
                                StoredFileRef completedFile) {
}
