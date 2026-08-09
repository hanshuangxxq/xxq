package com.xrq.xxq.module.practice.internship.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 培训课程更新请求（部分更新，字段可空）。
 */
@Data
public class TrainingUpdateRequest {

    private String title;
    private String description;
    private Long teacherId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
}
