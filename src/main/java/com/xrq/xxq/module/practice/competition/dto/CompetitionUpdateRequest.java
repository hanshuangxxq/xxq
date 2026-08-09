package com.xrq.xxq.module.practice.competition.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.competition.entity.CompetitionLevelEnum;

import lombok.Data;

/**
 * 竞赛更新请求（部分更新，字段可空）。
 */
@Data
public class CompetitionUpdateRequest {

    private String name;
    private String description;
    private String organizer;
    private CompetitionLevelEnum level;
    private LocalDateTime regStartTime;
    private LocalDateTime regEndTime;
    private LocalDateTime contestTime;
}
