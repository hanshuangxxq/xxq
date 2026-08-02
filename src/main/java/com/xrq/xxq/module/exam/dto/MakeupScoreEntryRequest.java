package com.xrq.xxq.module.exam.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 补考/重修成绩录入项（单科考试分数）。
 */
@Data
public class MakeupScoreEntryRequest {

    private Long studentUserId;
    private BigDecimal score; // 补考/重修考试分数
}
