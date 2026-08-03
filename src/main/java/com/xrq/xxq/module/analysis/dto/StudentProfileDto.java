package com.xrq.xxq.module.analysis.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 学生个人画像：聚合成绩、学分、绩点、趋势与排名的个性化学习画像。
 */
@Data
public class StudentProfileDto {

    private Long studentUserId;
    private String studentName;
    private String studentNo;
    private String className;
    private String majorName;
    private Integer enrollmentYear;

    private Long semesterId;
    private String semesterName;

    private BigDecimal cumulativeGpa;      // 累计 GPA
    private BigDecimal semesterGpa;        // 本学期 GPA
    private Integer totalCredits;          // 已选课程总学分
    private Integer earnedCredits;         // 已通过课程学分
    private Integer failCount;             // 累计挂科数（REGULAR 不及格）
    private Integer semesterFailCount;     // 本学期挂科数

    private Map<String, Integer> levelDistribution; // 优良中及格不及格 -> 数量
    private List<SemesterGpaTrend> semesterTrend;   // 学期 GPA 趋势
    private List<SubjectPerformance> subjects;      // 本学期各科表现
    private Integer classRank;             // 班级 GPA 排名（按累计 GPA 降序）
    private Integer classSize;             // 班级参评人数

    /** 学期绩点趋势点。 */
    @Data
    public static class SemesterGpaTrend {
        private Long semesterId;
        private String semesterName;
        private BigDecimal gpa;
        private BigDecimal avgScore;
        private Integer failCount;
    }

    /** 单科表现。 */
    @Data
    public static class SubjectPerformance {
        private Long courseId;
        private String courseName;
        private String courseType;       // 必修/选修/公选/实践
        private Integer credit;
        private BigDecimal totalScore;
        private String scoreLevel;       // 优良中及格不及格
        private BigDecimal gradePoint;   // 单科绩点
    }
}
