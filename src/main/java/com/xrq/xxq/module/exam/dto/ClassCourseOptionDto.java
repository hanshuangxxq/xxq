package com.xrq.xxq.module.exam.dto;

import lombok.Data;

/**
 * 按班级查询可排考课程的选项（教务建考用）。
 * <p>含建考所需 ID（teachInfoId/courseId），区别于学生侧脱敏的 ClassCourseDto。
 * className 为 teach_info 的合班全名（如 "计科2301,计科2302"），便于前端提示该课为合班。
 */
@Data
public class ClassCourseOptionDto {
    private Long teachInfoId;
    private Long courseId;
    private String courseName;
    private String teacherName;
    private String className;   // teach_info 的合班全名
    private Long semesterId;
    private String semesterName;
}
