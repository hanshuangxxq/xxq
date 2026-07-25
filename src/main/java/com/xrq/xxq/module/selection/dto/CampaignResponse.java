package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;

import lombok.Data;

@Data
public class CampaignResponse {
    private Long id;
    private String name;
    private Long semesterId;
    private String semesterName;
    private Long courseId;
    private Integer startWeek;
    private Integer endWeek;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private CampaignStatusEnum status;
    private LocalDateTime createTime;
    private Integer selectedCourseCount;
}
