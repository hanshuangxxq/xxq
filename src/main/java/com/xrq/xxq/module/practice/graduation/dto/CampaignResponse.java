package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.xrq.xxq.module.practice.graduation.entity.CampaignStatusEnum;

import lombok.Data;

/**
 * 毕设活动响应。
 */
@Data
public class CampaignResponse {

    private Long id;

    private String name;

    /** 参与年级ID列表 */
    private List<Long> allowedGradeIds;

    private LocalDateTime topicStartTime;

    private LocalDateTime topicEndTime;

    private Integer supervisorCapacity;

    private Integer freeSelectCapacity;

    private LocalDateTime openingStartTime;

    private LocalDateTime openingEndTime;

    private LocalDateTime midtermStartTime;

    private LocalDateTime midtermEndTime;

    private LocalDateTime thesisStartTime;

    private LocalDateTime thesisEndTime;

    private Integer advisorWeight;

    private Integer reviewerWeight;

    private Integer defenseWeight;

    private CampaignStatusEnum status;

    private LocalDateTime createTime;
}
