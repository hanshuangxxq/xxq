package com.xrq.xxq.module.file.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    /**
     * 可直接重传的分片序号（缺失或摘要不符）；为空表示服务端侧无问题。
     * <p>
     * 刻意加 {@code @JsonProperty}：Jackson 3 <b>不会</b>自动序列化 record 上非组件的派生方法，
     * 少了这个注解前端就拿不到该字段（已由 {@code FileControllerTest} 钉死）。
     * 由服务端给出而不是让前端自己合并 {@code missing} ∪ {@code badDigest}，
     * 是为了消除「客户端各写一份、其中一份写漏」的风险。
     */
    @JsonProperty("retryable")
    public List<Integer> retryable() {
        return java.util.stream.Stream.concat(missing.stream(), badDigest.stream())
                .distinct().sorted().toList();
    }
}
