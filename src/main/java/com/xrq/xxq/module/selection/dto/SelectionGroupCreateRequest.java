package com.xrq.xxq.module.selection.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

@Data
public class SelectionGroupCreateRequest {
    @NonNull
    private String name;
    @NonNull
    private Integer maxCourses;
}
