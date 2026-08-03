package com.xrq.xxq.module.analysis.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 学习进度：当前学期各课程完成度与考试/成绩状态（由 teach_info 周次 + exam 状态 + score 派生）。
 */
@Data
public class LearningProgressDto {

    private Long studentUserId;
    private String studentName;
    private String semesterName;
    private Integer currentWeek;   // 当前周（由学期起始日推算）
    private List<CourseProgress> courses;

    /** 单课程进度。 */
    @Data
    public static class CourseProgress {
        private Long teachInfoId;
        private Long courseId;
        private String courseName;
        private String teacherName;
        private Integer startWeek;
        private Integer endWeek;
        private Integer progressPercent;  // 0-100
        private String status;            // 进行中 / 已结课
        private String examStatus;        // 无考试 / 已排考 / 已完成
        private Boolean scoreEntered;     // 是否已出成绩
        private BigDecimal totalScore;
    }
}
