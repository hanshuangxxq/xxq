package com.xrq.xxq.module.score.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 成绩统计行：按课程聚合的分布数据。
 */
@Data
public class ScoreStatisticsDto {

    private Long courseId;
    private String courseName;
    private Integer totalCount;      // 有成绩人数
    private Integer excellentCount;  // 优
    private Integer goodCount;       // 良
    private Integer mediumCount;     // 中
    private Integer passCount;       // 及格
    private Integer failCount;       // 不及格
    private BigDecimal avgScore;     // 平均分
    private BigDecimal maxScore;     // 最高分
    private BigDecimal minScore;     // 最低分
    private BigDecimal passRate;     // 及格率（%）
}
