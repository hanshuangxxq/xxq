package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.graduation.entity.ProposalReviewStageEnum;

import lombok.Data;

/**
 * 审批流水条目（R-5.7 留痕展示）。
 */
@Data
public class ProposalReviewView {

    private ProposalReviewStageEnum stage;

    /** 动作 APPROVE/REJECT */
    private String action;

    /** 审批人 user.id */
    private Long reviewerId;

    private String reviewerName;

    private LocalDateTime reviewTime;

    private String comment;
}
