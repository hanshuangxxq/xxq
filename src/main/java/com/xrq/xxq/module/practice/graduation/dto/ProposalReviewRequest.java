package com.xrq.xxq.module.practice.graduation.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 选题审批动作（院系初审 / 教务终审共用，R-5.5/R-5.6）。
 * <p>
 * 驳回（approve=false）时 comment 必填，理由对学生可见。
 */
@Data
public class ProposalReviewRequest {

    /** 是否通过 */
    @NonNull
    private Boolean approve;

    /** 审批意见（驳回必填） */
    private String comment;
}
