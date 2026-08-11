package com.xrq.xxq.module.practice.graduation.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 指导教师形式审查论文（R-8.3：通过进入待查重 / 退回修改）。
 */
@Data
public class ThesisReviewRequest {

    /** 是否通过 */
    @NonNull
    private Boolean approve;

    /** 审查意见（退回必填） */
    private String comment;
}
