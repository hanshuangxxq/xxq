package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.xrq.xxq.module.practice.graduation.entity.ProposalStatusEnum;

import lombok.Data;

/**
 * 选题申请响应（含审批流水）。
 */
@Data
public class ProposalResponse {

    private Long id;

    private Long campaignId;

    private Long studentId;

    private String studentName;

    private String studentNo;

    private String title;

    private String content;

    private ProposalStatusEnum status;

    /** 最近一次驳回理由 */
    private String rejectReason;

    private LocalDateTime submitTime;

    /** 审批流水（按时间正序） */
    private List<ProposalReviewView> reviews;
}
