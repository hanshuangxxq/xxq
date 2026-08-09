package com.xrq.xxq.module.practice.internship.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.internship.entity.TrainingStatusEnum;

import lombok.Data;

@Data
public class TrainingResponse {

    private Long id;
    private Long semesterId;
    private String title;
    private String description;
    private Long teacherId;
    private String teacherName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
    private Integer enrolledCount;
    private TrainingStatusEnum status;
    private LocalDateTime createTime;
}
