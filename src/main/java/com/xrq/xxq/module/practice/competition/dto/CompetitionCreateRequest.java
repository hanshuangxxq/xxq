package com.xrq.xxq.module.practice.competition.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.competition.entity.CompetitionLevelEnum;

import lombok.Data;

/**
 * 竞赛创建请求（教务发布）。
 */
@Data
public class CompetitionCreateRequest {

    private Long semesterId;              // 可空，默认当前学期
    private String name;
    private String description;
    private String organizer;
    private CompetitionLevelEnum level;
    private LocalDateTime regStartTime;
    private LocalDateTime regEndTime;
    private LocalDateTime contestTime;
}
