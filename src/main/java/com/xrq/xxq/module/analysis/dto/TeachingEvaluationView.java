package com.xrq.xxq.module.analysis.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 评教返回视图（富化课程名/教师名/学期名 + 指标得分明细）。
 */
@Data
public class TeachingEvaluationView {

    private Long id;
    private Long teachInfoId;
    private Long courseId;
    private String courseName;
    private Long teacherId;
    private String teacherName;
    private Long semesterId;
    private String semesterName;
    private Long templateId;
    private String templateName;
    private List<EvaluationScoreView> items;  // 各指标得分明细
    private BigDecimal avgScore;
    private String comment;
    private LocalDateTime createTime;
}
