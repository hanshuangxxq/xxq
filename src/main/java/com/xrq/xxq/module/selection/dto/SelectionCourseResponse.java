package com.xrq.xxq.module.selection.dto;

import java.util.List;

import lombok.Data;

@Data
public class SelectionCourseResponse {
    private Long id;
    private Long campaignId;
    private Long courseId;
    private String courseName;
    private String courseCode;
    private Integer credit;
    private Integer courseHour;
    private String description;
    private String courseType;
    private List<Long> allowedGradeIds;
    private List<Long> allowedMajors;
    private List<Long> timeRestrictionIds;
    private Long groupId;
    private String groupName;
    private Integer capacity;
    private Integer selectedCount;
    private Integer remaining;
    private Boolean selectedByMe;
}
