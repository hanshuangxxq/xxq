package com.xrq.xxq.module.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudentDto {
    private Long studentId;
    private String studentNo;
    private String grade;
    private String majorName;
    private String className;
    private Integer enrollmentYear;

    private Long userId;
    private String name;
    private String email;
    private String phone;
    private LocalDateTime createTime;
}
