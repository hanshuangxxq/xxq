package com.xrq.xxq.module.analysis.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 课程成绩跨学期趋势。
 */
@Data
public class ScoreTrendDto {

    private Long courseId;
    private String courseName;
    private List<SemesterPoint> points;

    /** 单学期统计点。 */
    @Data
    public static class SemesterPoint {
        private Long semesterId;
        private String semesterName;
        private Integer totalCount;
        private BigDecimal avgScore;
        private BigDecimal passRate;
    }
}
