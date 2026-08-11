package com.xrq.xxq.module.practice.graduation.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 院系改派学生（R-6.13，选题截止后）。
 */
@Data
public class ReassignRequest {

    /** 活动ID */
    @NonNull
    private Long campaignId;

    /** 学生 user.id */
    @NonNull
    private Long studentId;

    /** 新教师 user.id */
    @NonNull
    private Long newTeacherId;

    /** 改派原因 */
    @NonNull
    private String reason;
}
