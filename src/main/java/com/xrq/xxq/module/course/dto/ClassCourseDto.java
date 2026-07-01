package com.xrq.xxq.module.course.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassCourseDto {
    private String courseName;
    private String teacherName;
    private Integer dayOfWeek;
    private Integer week;
    private Long timeId;
    private String building;
    private String classroom;
}
