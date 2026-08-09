package com.xrq.xxq.module.practice.competition.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 竞赛结果记录（获奖信息）。
 * <p>
 * 一个报名记录对应一条结果；student_id 为报名人 user.id。
 */
@Data
@TableName("competition_result")
public class CompetitionResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long competitionId;
    private Long registrationId;
    private Long studentId;                // 获奖学生 user.id
    private AwardEnum award;
    private Integer score;
    private String comment;
    private LocalDateTime awardTime;

    @TableLogic
    private Integer deleted;
}
