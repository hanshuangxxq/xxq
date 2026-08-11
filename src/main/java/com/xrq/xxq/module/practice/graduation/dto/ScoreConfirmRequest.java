package com.xrq.xxq.module.practice.graduation.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 院系确认并发布总评成绩（R-9.3，确认后学生可见、不可再改）。
 */
@Data
public class ScoreConfirmRequest {

    /** 活动ID */
    @NonNull
    private Long campaignId;

    /** 学生 user.id */
    @NonNull
    private Long studentId;
}
