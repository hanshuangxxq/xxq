package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.xrq.xxq.module.practice.graduation.entity.CampaignStatusEnum;

import lombok.Data;

/**
 * 选题活动响应。
 */
@Data
public class CampaignResponse {

    private Long id;
    private Long semesterId;
    private String title;
    private LocalDateTime deadline;
    private Integer supervisorCapacity;
    private Integer freeSelectCapacity;
    private List<Long> allowedGradeIds;
    private CampaignStatusEnum status;
    private LocalDateTime createTime;
}
