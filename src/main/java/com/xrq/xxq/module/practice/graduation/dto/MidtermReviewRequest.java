package com.xrq.xxq.module.practice.graduation.dto;

import org.jspecify.annotations.NonNull;

import com.xrq.xxq.module.practice.graduation.entity.MidtermConclusionEnum;

import lombok.Data;

/**
 * 指导教师审核中期检查并给出结论（R-7.5：正常/警告/严重滞后）。
 */
@Data
public class MidtermReviewRequest {

    @NonNull
    private MidtermConclusionEnum conclusion;

    /** 审核意见 */
    private String comment;
}
