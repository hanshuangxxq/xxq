package com.xrq.xxq.module.selection.dto;

import java.util.List;

import org.jspecify.annotations.NonNull;

import com.xrq.xxq.module.course.entity.CurseEnum;

import lombok.Data;

@Data
public class SelectionCourseAddRequest {
    @NonNull
    private Long groupId;
    @NonNull
    private String courseName;
    @NonNull
    private String courseCode;
    @NonNull
    private Integer credit;
    private Integer courseHour;
    private String description;
    private CurseEnum courseType;
    private List<Long> allowedGradeIds;
    private List<Long> allowedMajors;
    private List<Long> timeRestrictionIds;
    @NonNull
    private Integer capacity;
}
