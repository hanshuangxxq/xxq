package com.xrq.xxq.module.analysis.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 同课程各班级成绩横向对比。
 */
@Data
public class ScoreComparisonDto {

    private Long courseId;
    private String courseName;
    private Long semesterId;
    private String semesterName;
    private List<ClassPoint> classes;

    /** 单班级统计点。 */
    @Data
    public static class ClassPoint {
        private String className;
        private Integer totalCount;
        private BigDecimal avgScore;
        private BigDecimal passRate;
        private Integer failCount;
    }
}
