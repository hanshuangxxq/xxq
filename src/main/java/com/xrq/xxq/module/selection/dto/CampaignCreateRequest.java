package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;

import org.jspecify.annotations.NonNull;

import lombok.Data;

@Data
public class CampaignCreateRequest {
    @NonNull
    private String name;
    @NonNull
    private Long semesterId;
    @NonNull
    private LocalDateTime startTime;
    @NonNull
    private LocalDateTime endTime;
    private Integer startWeek;
    private Integer endWeek;
    /**
     * 选课组 ID。可选；非空时在创建活动的同时绑定到该组。
     */
    private Long groupId;
}
