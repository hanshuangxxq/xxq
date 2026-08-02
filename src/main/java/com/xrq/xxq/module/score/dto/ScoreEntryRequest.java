package com.xrq.xxq.module.score.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 单条成绩录入项。
 */
@Data
public class ScoreEntryRequest {

    private Long studentUserId;   // 学生 user.id
    private BigDecimal regularScore; // 平时分
    private BigDecimal finalScore;   // 期末成绩
}
