package com.xrq.xxq.module.analysis.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 班级/专业成绩跨学期趋势。
 */
@Data
public class ClassTrendDto {

    private String groupKey;
    private String groupType;      // class / major
    private List<SemesterPoint> points;

    /** 单学期统计点。 */
    @Data
    public static class SemesterPoint {
        private Long semesterId;
        private String semesterName;
        private BigDecimal avgScore;
        private BigDecimal gpa;
        private BigDecimal passRate;
        private Integer studentCount;
    }
}
