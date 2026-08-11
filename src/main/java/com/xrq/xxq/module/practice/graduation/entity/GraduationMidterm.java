package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 中期检查（阶段二 R-7.4/R-7.5）。
 * <p>
 * 学生提交进展情况 → 指导教师审核并给出中期结论（正常/警告/严重滞后），
 * 结论进入教务看板供预警。状态复用共享枚举 {@code ReportStatusEnum}（SUBMITTED/REVIEWED）。
 */
@Data
@TableName("graduation_midterm")
public class GraduationMidterm {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动ID graduation_campaign.id */
    private Long campaignId;

    /** 师生匹配ID graduation_assignment.id */
    private Long assignmentId;

    /** 学生 user.id */
    private Long studentId;

    /** 进展情况/已完成工作/后续计划 */
    private String content;

    /** 磁盘存储文件名（UUID） */
    private String fileName;

    /** 原始文件名 */
    private String fileOriginal;

    /** 状态 SUBMITTED/REVIEWED */
    private String status;

    private MidtermConclusionEnum conclusion;

    private LocalDateTime submitTime;

    /** 审核教师 user.id */
    private Long reviewTeacherId;

    private String reviewComment;

    private LocalDateTime reviewTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
