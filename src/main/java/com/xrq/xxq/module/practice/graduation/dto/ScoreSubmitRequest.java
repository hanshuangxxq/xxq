package com.xrq.xxq.module.practice.graduation.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 录入分项成绩（R-9.3，指导/评阅/答辩三个入口共用）。
 */
@Data
public class ScoreSubmitRequest {

    /** 活动ID */
    @NonNull
    private Long campaignId;

    /** 学生 user.id */
    @NonNull
    private Long studentId;

    /** 分项得分（0-100） */
    @NonNull
    private Integer score;
}
