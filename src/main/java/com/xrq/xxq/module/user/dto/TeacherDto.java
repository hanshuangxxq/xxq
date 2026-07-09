package com.xrq.xxq.module.user.dto;

import lombok.Data;

/**
 * 教师信息返回结果类
 */
@Data
public class TeacherDto {
    private Long id;
    private String name;
    private String teacherNo;
    private String title;
    private String department;
}
