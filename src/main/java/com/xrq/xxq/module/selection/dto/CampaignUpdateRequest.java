package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class CampaignUpdateRequest {
    private String name;
    private Long semesterId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxCoursesPerStudent;
}
