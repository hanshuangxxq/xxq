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

    /** 学号（教师录入列表等场景填充，其余场景为 null） */
    private String studentNo;

    /** 答辩组（评阅录入列表场景填充，其余场景为 null） */
    private String groupName;

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
