package com.xrq.xxq.module.analysis.dto;

import java.math.BigDecimal;
import java.util.Map;

import lombok.Data;

/**
 * 教师教学质量评估：评教侧（均分/人数/指标）+ 成绩侧（均分/及格率/课程数）。
 */
@Data
public class TeacherQualityDto {

    private Long teacherId;
    private String teacherName;
    private String department;

    // 评教侧
    private BigDecimal avgEvaluationScore;              // 评教均分（avg_score 均值）
    private Integer evalCount;                          // 评教数
    private Map<String, BigDecimal> itemAverages;       // 指标名 -> 均分（按 teaching_evaluation_score 快照名分组）

    // 成绩侧
    private Integer courseCount;       // 授课课程数
    private BigDecimal courseAvgScore; // 所授课程均分
    private BigDecimal coursePassRate; // 所授课程及格率（%）
    private Integer studentCount;      // 授课学生人次
}
