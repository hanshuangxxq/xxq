package com.xrq.xxq.module.analysis.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 成绩分数段分布：按 [0-59][60-69][70-79][80-89][90-100] 分段统计。
 */
@Data
public class ScoreDistributionDto {

    private Long courseId;
    private String courseName;
    private Integer totalCount;      // 有成绩人数
    private Integer seg0to59;        // 0-59 不及格
    private Integer seg60to69;       // 60-69
    private Integer seg70to79;       // 70-79
    private Integer seg80to89;       // 80-89
    private Integer seg90to100;      // 90-100
    private BigDecimal avgScore;     // 平均分
    private BigDecimal maxScore;     // 最高分
    private BigDecimal minScore;     // 最低分
    private BigDecimal passRate;     // 及格率（%）
    private BigDecimal stddev;       // 标准差
}
