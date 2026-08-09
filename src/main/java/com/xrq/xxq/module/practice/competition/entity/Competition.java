package com.xrq.xxq.module.practice.competition.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 竞赛实体。
 */
@Data
@TableName("competition")
public class Competition {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long semesterId;
    private String name;
    private String description;
    private String organizer;              // 主办方
    private CompetitionLevelEnum level;
    private LocalDateTime regStartTime;    // 报名开始时间
    private LocalDateTime regEndTime;      // 报名截止时间
    private LocalDateTime contestTime;     // 比赛时间
    private CompetitionStatusEnum status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
