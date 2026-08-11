package com.xrq.xxq.module.practice.graduation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.graduation.entity.GraduationScoreStatusEnum;

import lombok.Data;

/**
 * 毕设成绩响应。
 */
@Data
public class ScoreResponse {

    private Long id;

    private Long campaignId;

    private Long studentId;

    private String studentName;

    private Integer advisorScore;

    private Long advisorBy;

    private String advisorName;

    private LocalDateTime advisorTime;

    private Integer reviewerScore;

    private Long reviewerBy;

    private String reviewerName;

    private LocalDateTime reviewerTime;

    private Integer defenseScore;

    private Long defenseBy;

    private String defenseName;

    private LocalDateTime defenseTime;

    private BigDecimal totalScore;

    private GraduationScoreStatusEnum status;

    private Long confirmBy;

    private String confirmName;

    private LocalDateTime confirmTime;

    private LocalDateTime publishTime;
}
