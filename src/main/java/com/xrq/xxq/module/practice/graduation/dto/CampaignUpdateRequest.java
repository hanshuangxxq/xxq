package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 更新毕设活动（全字段可空，仅更新非空字段）。
 * <p>
 * R-4.1：选题开始后仅允许延长截止时间、上调名额，不允许缩短时间或下调名额。
 */
@Data
public class CampaignUpdateRequest {

    private String name;

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
}
