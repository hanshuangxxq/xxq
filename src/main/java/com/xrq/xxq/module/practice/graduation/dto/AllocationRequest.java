package com.xrq.xxq.module.practice.graduation.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 院系指定分配学生给教师（R-6.7~R-6.10，选题截止后开放）。
 */
@Data
public class AllocationRequest {

    /** 活动ID */
    @NonNull
    private Long campaignId;

    /** 学生 user.id */
    @NonNull
    private Long studentId;

    /** 目标教师 user.id */
    @NonNull
    private Long teacherId;
}
