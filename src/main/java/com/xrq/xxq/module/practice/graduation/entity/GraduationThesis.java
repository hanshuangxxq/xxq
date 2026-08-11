package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 论文（阶段三 R-8.1/R-8.2 版本管理）。
 * <p>
 * 同一师生匹配可有多版本记录（每重提一行），保留最近 N 版（默认3），最新版有效；
 * 状态机见 {@link ThesisStatusEnum}：SUBMITTED → APPROVED → DUPLICATE_PASSED/DUPLICATE_FAILED。
 */
@Data
@TableName("graduation_thesis")
public class GraduationThesis {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动ID graduation_campaign.id */
    private Long campaignId;

    /** 师生匹配ID graduation_assignment.id */
    private Long assignmentId;

    /** 学生 user.id */
    private Long studentId;

    /** 论文题目 */
    private String title;

    /** 磁盘存储文件名（UUID） */
    private String fileName;

    /** 原始文件名 */
    private String fileOriginal;

    /** 版本号（每次重提+1） */
    private Integer version;

    /** 是否最新版 1:是 0:否 */
    private Integer isLatest;

    private ThesisStatusEnum status;

    private LocalDateTime submitTime;

    /** 形式审查教师 user.id */
    private Long reviewTeacherId;

    private String reviewComment;

    private LocalDateTime reviewTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
