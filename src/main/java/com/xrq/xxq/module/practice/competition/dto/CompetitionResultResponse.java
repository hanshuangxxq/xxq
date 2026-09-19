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

    /** 获奖证书存储路径（有值即表示可下载） */
    private String fileName;

    /** 获奖证书展示文件名 */
    private String fileOriginal;

    private LocalDateTime awardTime;
}
