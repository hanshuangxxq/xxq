package com.xrq.xxq.module.coursework.assignment.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 教师批改请求：逐题给分（仅大题，客观题不可人工改判）+ 整份评语。 */
@Data
public class GradeRequest {

    @NonNull
    private List<GradeItem> items;

    /** 整份评语（可空）。 */
    private String comment;

    /** 单题批改项。 */
    @Data
    public static class GradeItem {

        @NonNull
        private Long answerId;

        /** 本题得分（0 .. 题分）。 */
        @NonNull
        private BigDecimal score;

        /** 逐题评语（可空）。 */
        private String comment;
    }
}
