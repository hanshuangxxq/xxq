package com.xrq.xxq.module.practice.socialpractice.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import com.xrq.xxq.module.practice.common.entity.AuditStatusEnum;

import lombok.Data;

/**
 * 社会实践申报记录（个人/团队 + 教务审核）。
 * <p>
 * student_id 存申报人 user.id；team_name/members 仅团队申报填写。
 */
@Data
@TableName("social_practice_application")
public class SocialPracticeApplication {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long practiceId;
    private Long studentId;               // 申报人 user.id
    private String teamName;              // 团队名（个人为空）
    private String members;               // 团队成员 user.id 逗号分隔（个人为空）
    private AuditStatusEnum status;
    private String applyReason;
    private LocalDateTime applyTime;
    private LocalDateTime reviewTime;
    private String reviewComment;

    @TableLogic
    private Integer deleted;
}
