package com.xrq.xxq.module.selection.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

@Data
public class SelectionCourseAddRequest {
    @NonNull
    private Long courseId;
    @NonNull
    private Integer capacity;
}
