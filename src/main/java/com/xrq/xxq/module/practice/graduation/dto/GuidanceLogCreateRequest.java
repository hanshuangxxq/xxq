package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import org.jspecify.annotations.NonNull;

import com.xrq.xxq.module.practice.graduation.entity.GuidanceFormEnum;

import lombok.Data;

/**
 * 教师记录过程指导日志（R-7.7）。
 */
@Data
public class GuidanceLogCreateRequest {

    /** 活动ID */
    @NonNull
    private Long campaignId;

    /** 学生 user.id */
    @NonNull
    private Long studentId;

    /** 指导时间 */
    @NonNull
    private LocalDateTime logTime;

    @NonNull
    private GuidanceFormEnum form;

    /** 指导内容摘要 */
    @NonNull
    private String summary;
}
