package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.xrq.xxq.module.practice.graduation.entity.CampaignStatusEnum;

import lombok.Data;

/**
 * 选题活动更新请求（教务，部分更新）。
 */
@Data
public class CampaignUpdateRequest {

    private String title;
    private LocalDateTime deadline;
    private Integer supervisorCapacity;
    private Integer freeSelectCapacity;
    private List<Long> allowedGradeIds;
    private CampaignStatusEnum status;
}
