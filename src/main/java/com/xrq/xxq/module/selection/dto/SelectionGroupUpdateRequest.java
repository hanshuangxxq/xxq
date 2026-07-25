package com.xrq.xxq.module.selection.dto;

import lombok.Data;

@Data
public class SelectionGroupUpdateRequest {
    private String name;
    private Integer maxCourses;
    private Integer sortOrder;
}
