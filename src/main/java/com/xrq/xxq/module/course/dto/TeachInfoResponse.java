package com.xrq.xxq.module.course.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeachInfoResponse {
    private Long id;
    // 课程信息
    private Long courseId;
    private String courseName;
    private String courseCode;
    private Integer credit;
    private Integer courseHour;
    private String courseType;
    // 教师信息
    private Long teacherId;
    private String teacherName;
    private String teacherNo;
    private String title;
    private String department;
    // 班级信息
    private String className;
    private String college;
    // 上课时间
    private Long timeId;
    private Integer dayOfWeek;
    private LocalTime startPeriod;
    private LocalTime endPeriod;
    // 上课地点
    private Long localId;
    private String building;
    private String classroom;
}
