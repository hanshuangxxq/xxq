package com.xrq.xxq.module.score.dto;

import lombok.Data;

/**
 * 成绩复核申请请求。
 */
@Data
public class ReviewApplyRequest {

    private Long scoreId;  // 关联 grade.id
    private String reason; // 申请理由
}
