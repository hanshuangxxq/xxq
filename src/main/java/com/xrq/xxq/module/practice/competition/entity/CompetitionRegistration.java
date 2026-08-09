package com.xrq.xxq.module.practice.competition.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import com.xrq.xxq.module.practice.common.entity.AuditStatusEnum;

import lombok.Data;

/**
 * 竞赛报名记录（个人/团队）。
 * <p>
 * student_id 存报名人 user.id；team_name/members 仅团队赛填写，
 * members 为团队成员 user.id 逗号分隔。
 */
@Data
@TableName("competition_registration")
public class CompetitionRegistration {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long competitionId;
    private Long studentId;                // 报名人 user.id
    private String teamName;               // 团队名（个人赛为空）
    private String members;                // 团队成员 user.id 逗号分隔（个人赛为空）
    private AuditStatusEnum status;
    private LocalDateTime registerTime;
    private LocalDateTime reviewTime;
    private String reviewComment;

    @TableLogic
    private Integer deleted;
}
