package com.xrq.xxq.module.coursework.assignment.dto;

import java.math.BigDecimal;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 从题库抽题：可逐题覆盖配分（null 用题库默认配分）。 */
@Data
public class QuestionRefInput {

    @NonNull
    private Long questionId;

    private BigDecimal score;
}
