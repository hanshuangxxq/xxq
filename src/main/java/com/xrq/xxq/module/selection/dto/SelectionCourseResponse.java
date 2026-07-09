package com.xrq.xxq.module.selection.dto;

import lombok.Data;

@Data
public class SelectionCourseResponse {
    private Long id;
    private Long campaignId;
    private Long courseId;
    private String courseName;
    private String courseCode;
    private Integer credit;
    private String courseType;
    private Integer capacity;
    private Integer selectedCount;
    private Integer remaining;
    private Boolean selectedByMe;
}
