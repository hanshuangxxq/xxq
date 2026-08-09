package com.xrq.xxq.module.practice.competition.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.competition.entity.CompetitionLevelEnum;
import com.xrq.xxq.module.practice.competition.entity.CompetitionStatusEnum;

import lombok.Data;

@Data
public class CompetitionResponse {

    private Long id;
    private Long semesterId;
    private String name;
    private String description;
    private String organizer;
    private CompetitionLevelEnum level;
    private LocalDateTime regStartTime;
    private LocalDateTime regEndTime;
    private LocalDateTime contestTime;
    private CompetitionStatusEnum status;
    private LocalDateTime createTime;
}
