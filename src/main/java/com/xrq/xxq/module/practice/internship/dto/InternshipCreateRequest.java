package com.xrq.xxq.module.practice.internship.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 实习项目创建请求（院系管理者发布）。
 */
@Data
public class InternshipCreateRequest {

    private Long semesterId;          // 可空，默认当前学期
    private String title;
    private String company;
    private String description;
    private Long supervisorId;         // 负责教师 user.id；可空，默认创建者
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
}
