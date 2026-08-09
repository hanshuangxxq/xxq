package com.xrq.xxq.module.practice.internship.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.internship.entity.InternshipStatusEnum;

import lombok.Data;

@Data
public class InternshipResponse {

    private Long id;
    private Long semesterId;
    private String title;
    private String company;
    private String description;
    private Long supervisorId;
    private String supervisorName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
    private Integer selectedCount;      // 已通过人数（占容量）
    private Integer pendingCount;       // 待审核人数（Redis 跟踪，不占容量）
    private InternshipStatusEnum status;
    private LocalDateTime createTime;
}
