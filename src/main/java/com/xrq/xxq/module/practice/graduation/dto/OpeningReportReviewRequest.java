package com.xrq.xxq.module.practice.graduation.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 指导教师审核开题报告（R-7.2：通过 / 驳回修改）。
 */
@Data
public class OpeningReportReviewRequest {

    /** 是否通过 */
    @NonNull
    private Boolean approve;

    /** 审核意见（驳回必填，对学生可见） */
    private String comment;
}
