package com.xrq.xxq.module.score.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 教师回复复核申请：可附带调整后的总评成绩（可选）。
 */
@Data
public class ReviewReplyRequest {

    private String reply;           // 回复内容
    private BigDecimal newTotalScore; // 调整后的总评（可选；为空表示不调分）
}
