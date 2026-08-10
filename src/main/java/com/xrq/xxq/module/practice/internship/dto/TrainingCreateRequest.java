package com.xrq.xxq.module.practice.internship.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 培训课程创建请求（院系管理者发布）。
 */
@Data
public class TrainingCreateRequest {

    private Long semesterId;          // 可空，默认当前学期
    private String title;
    private String description;
    private Long teacherId;           // 授课教师 user.id；可空，默认创建者
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
}
