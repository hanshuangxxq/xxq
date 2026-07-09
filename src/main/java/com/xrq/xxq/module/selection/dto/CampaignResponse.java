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
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxCoursesPerStudent;
    private CampaignStatusEnum status;
    private LocalDateTime createTime;
    private Integer selectedCourseCount;
}
