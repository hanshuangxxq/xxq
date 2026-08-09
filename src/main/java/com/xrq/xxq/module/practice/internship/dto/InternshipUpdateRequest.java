package com.xrq.xxq.module.practice.internship.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 实习项目更新请求（部分更新，字段可空）。
 */
@Data
public class InternshipUpdateRequest {

    private String title;
    private String company;
    private String description;
    private Long supervisorId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
}
