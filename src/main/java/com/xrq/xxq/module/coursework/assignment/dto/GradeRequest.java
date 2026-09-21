package com.xrq.xxq.module.coursework.assignment.dto;

import java.math.BigDecimal;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 教师批改请求。 */
@Data
public class GradeRequest {

    @NonNull
    private BigDecimal score;   // 0 .. 作业满分

    private String comment;
}
