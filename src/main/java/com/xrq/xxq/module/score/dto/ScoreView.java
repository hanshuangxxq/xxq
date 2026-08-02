package com.xrq.xxq.module.score.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xrq.xxq.module.score.entity.ScoreTypeEnum;

import lombok.Data;

/**
 * 成绩返回视图（富化课程名/教师名/学生姓名学号）。
 */
@Data
public class ScoreView {

    private Long id;
    private Long teachInfoId;
    private Long courseId;
    private String courseName;
    private Long teacherId;
    private String teacherName;
    private Long studentUserId;
    private String studentName;
    private String studentNo;
    private Long semesterId;
    private BigDecimal regularScore;
    private BigDecimal finalScore;
    private Integer regularRatio;
    private BigDecimal totalScore;
    private String scoreLevel;
    private ScoreTypeEnum scoreType;
    private Integer locked;
    private LocalDateTime createTime;
}
