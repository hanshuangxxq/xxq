package com.xrq.xxq.module.practice.competition.dto;

import com.xrq.xxq.module.practice.competition.entity.AwardEnum;

import lombok.Data;

/**
 * 竞赛结果录入请求（教务）。
 */
@Data
public class CompetitionResultRequest {

    private Long competitionId;
    private Long registrationId;
    private AwardEnum award;
    private Integer score;
    private String comment;
}
