package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class CampaignUpdateRequest {
    private String name;
    private Long semesterId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer startWeek;
    private Integer endWeek;
    /**
     * 选课组 ID。可选；非空且与当前绑定不同时触发换绑（要求 DRAFT 状态）。
     * 为 null 时不修改绑定关系。
     */
    private Long groupId;
}
