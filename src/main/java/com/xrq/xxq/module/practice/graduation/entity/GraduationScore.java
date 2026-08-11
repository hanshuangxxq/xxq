package com.xrq.xxq.module.practice.graduation.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 毕设成绩（阶段四 R-9.2/R-9.3）。
 * <p>
 * 三项分项评分（指导教师/评阅教师/答辩）分别录入，齐全后按活动权重自动合成总评；
 * 院系管理者确认后发布给学生（发布后不可再改）。
 */
@Data
@TableName("graduation_score")
public class GraduationScore {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动ID graduation_campaign.id */
    private Long campaignId;

    /** 学生 user.id */
    private Long studentId;

    /** 指导教师评分（0-100） */
    private Integer advisorScore;

    /** 指导评分人 user.id */
    private Long advisorBy;

    private LocalDateTime advisorTime;

    /** 评阅教师评分（0-100） */
    private Integer reviewerScore;

    /** 评阅评分人 user.id */
    private Long reviewerBy;

    private LocalDateTime reviewerTime;

    /** 答辩评分（0-100） */
    private Integer defenseScore;

    /** 答辩评分人 user.id */
    private Long defenseBy;

    private LocalDateTime defenseTime;

    /** 总评成绩（按活动权重合成） */
    private BigDecimal totalScore;

    private GraduationScoreStatusEnum status;

    /** 院系确认人 user.id */
    private Long confirmBy;

    private LocalDateTime confirmTime;

    private LocalDateTime publishTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
