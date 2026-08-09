package com.xrq.xxq.module.practice.competition.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.competition.entity.AwardEnum;

import lombok.Data;

@Data
public class CompetitionResultResponse {

    private Long id;
    private Long competitionId;
    private String competitionName;
    private Long registrationId;
    private Long studentId;
    private String studentName;
    private AwardEnum award;
    private Integer score;
    private String comment;
    private LocalDateTime awardTime;
}
