package com.xrq.xxq.module.practice.graduation.dto;

import lombok.Data;

/**
 * 院系初审请求（院系管理者，仅本学院）。
 */
@Data
public class ProposalReviewRequest {

    private Boolean approved;       // true 通过(入池) / false 驳回
    private String comment;         // 审核意见
}
