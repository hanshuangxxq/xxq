package com.xrq.xxq.module.analysis.dto;

import java.util.List;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 评教提交请求：按所评教模板的指标提交各单项评分（1-max_score）+ 可选评语。
 */
@Data
public class EvaluationSubmitRequest {

    @NonNull
    private Long teachInfoId;
    @NonNull
    private List<ScoreItemDto> scores;  // 各指标评分
    private String comment;
}
