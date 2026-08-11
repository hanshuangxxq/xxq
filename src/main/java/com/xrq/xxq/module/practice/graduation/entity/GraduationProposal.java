package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 选题申请（学生自拟题目）。
 * <p>
 * 一名学生在同一活动同一时间仅一条有效申请（R-5.3）；
 * 被驳回后可在截止前修改重提，重新走完整两级审批流程。
 */
@Data
@TableName("graduation_proposal")
public class GraduationProposal {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动ID graduation_campaign.id */
    private Long campaignId;

    /** 学生 user.id */
    private Long studentId;

    /** 自拟题目名称 */
    private String title;

    /** 主要内容说明（不少于100字） */
    private String content;

    private ProposalStatusEnum status;

    /** （最近一次）提交时间 */
    private LocalDateTime submitTime;

    /** 最近一次驳回理由 */
    private String rejectReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
