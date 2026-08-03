package com.xrq.xxq.module.analysis.dto;

import java.math.BigDecimal;
import java.util.Map;

import lombok.Data;

/**
 * 班级/专业成绩聚合分析：单组（一个班级或一个专业）的统计指标。
 */
@Data
public class ClassAnalysisDto {

    private String groupKey;       // 班级名 或 专业名
    private String groupType;      // class / major
    private Integer studentCount;  // 参评学生数
    private Integer scoreCount;    // REGULAR 成绩条数
    private BigDecimal avgScore;   // 平均分
    private BigDecimal gpa;        // 组内加权 GPA
    private BigDecimal passRate;   // 及格率（%）
    private Integer failCount;     // 挂科人次
    private Map<String, Integer> levelDistribution; // 优良中及格不及格 -> 数量
}
