package com.xrq.xxq.module.practice.internship.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.internship.entity.EnrollStatusEnum;

import lombok.Data;

@Data
public class TrainingEnrollmentResponse {

    private Long id;
    private Long courseId;
    private String courseTitle;
    private Long studentId;
    private String studentName;
    private LocalDateTime enrollTime;
    private EnrollStatusEnum status;
}
