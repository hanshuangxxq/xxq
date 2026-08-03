package com.xrq.xxq.module.analysis.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 评教提交单项：指标 id + 评分。
 */
@Data
public class ScoreItemDto {

    @NonNull
    private Long itemId;
    @NonNull
    private Integer score;
}
