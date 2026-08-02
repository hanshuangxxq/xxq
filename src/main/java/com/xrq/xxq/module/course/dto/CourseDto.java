package com.xrq.xxq.module.course.dto;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseDto {
    // 授课安排 id（teach_info.id）：成绩录入/考试按此关联，课表视图亦返回以供前端定位
    private Long id;
    // 课程信息（脱敏：不含 courseId、courseCode）
    private String courseName;
    private Integer credit;
    private Integer courseHour;
    private String courseType;
    // 教师信息（脱敏：不含 teacherId、teacherNo、title）
    private String teacherName;
    private String department;
    // 班级信息
    private String className;
    private String college;
    // 上课时间
    private Integer dayOfWeek;
    private Integer startWeek;
    private Integer endWeek;
    private Long timeId;
    private LocalTime startPeriod; // 上课开始时间（来自 time 表）
    private LocalTime endPeriod;   // 上课结束时间（来自 time 表）
    // 上课地点（脱敏：不含 localId）
    private String building;
    private String classroom;
}
