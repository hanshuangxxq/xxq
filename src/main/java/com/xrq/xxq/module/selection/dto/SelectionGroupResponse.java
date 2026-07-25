package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SelectionGroupResponse {
    private Long id;
    private Long campaignId;
    private String name;
    private Integer maxCourses;
    private Integer sortOrder;
    private Integer courseCount;
    private LocalDateTime createTime;
}
