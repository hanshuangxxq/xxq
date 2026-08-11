package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 选题审批流水（两级审批留痕 R-5.7）。
 * <p>
 * 每次审批（院系初审/教务终审，通过/驳回）追加一行，审批人/时间/意见可查。
 */
@Data
@TableName("graduation_proposal_review")
public class GraduationProposalReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 选题申请ID graduation_proposal.id */
    private Long proposalId;

    /** 审批级别 DEPT/ACADEMIC */
    private ProposalReviewStageEnum stage;

    /** 动作 APPROVE/REJECT */
    private String action;

    /** 审批人 user.id */
    private Long reviewerId;

    private LocalDateTime reviewTime;

    /** 审批意见（驳回必填） */
    private String comment;

    private LocalDateTime createTime;
}
