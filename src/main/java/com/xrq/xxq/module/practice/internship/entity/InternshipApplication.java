package com.xrq.xxq.module.practice.internship.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import com.xrq.xxq.module.practice.common.entity.AuditStatusEnum;

import lombok.Data;

/**
 * 实习报名记录（学生报名 + 教师审核）。
 * <p>
 * student_id 存学生 user.id。
 */
@Data
@TableName("internship_application")
public class InternshipApplication {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long internshipId;
    private Long studentId;             // 学生 user.id
    private AuditStatusEnum status;
    private String applyReason;
    private LocalDateTime applyTime;
    private LocalDateTime reviewTime;
    private String reviewComment;

    @TableLogic
    private Integer deleted;
}
