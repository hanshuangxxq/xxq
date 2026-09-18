package com.xrq.xxq.module.file.dto;

import java.util.List;

/**
 * 分片校验结果：把「整文件校验失败」从「全量重传」降级为「精准重传坏片」。
 *
 * @param received    服务端已收分片序号（升序）
 * @param missing     应有但缺失/尺寸不符的分片序号
 * @param badDigest   摘要与上传时声明不符的分片序号（磁盘静默损坏）
 * @param hashesKnown 是否掌握分片摘要基线。会话从磁盘重建（Redis 曾丢失）时为 {@code false}，
 *                    此时只能做尺寸校验，{@code badDigest} 恒为空
 */
public record VerifyResult(List<Integer> received, List<Integer> missing, List<Integer> badDigest,
                           boolean hashesKnown) {

    /** 可直接重传的分片序号（缺失或摘要不符）；为空表示服务端侧无问题。 */
    public List<Integer> retryable() {
        return java.util.stream.Stream.concat(missing.stream(), badDigest.stream())
                .distinct().sorted().toList();
    }
}
