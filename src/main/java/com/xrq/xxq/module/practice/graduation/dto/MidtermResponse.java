package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.graduation.entity.MidtermConclusionEnum;

import lombok.Data;

/**
 * 中期检查响应。
 */
@Data
public class MidtermResponse {

    private Long id;

    private Long campaignId;

    private Long assignmentId;

    private Long studentId;

    private String studentName;

    private String content;

    /** 原始文件名（有附件时） */
    private String fileOriginal;

    /** 状态 SUBMITTED/REVIEWED */
    private String status;

    private MidtermConclusionEnum conclusion;

    private LocalDateTime submitTime;

    private Long reviewTeacherId;

    private String reviewTeacherName;

    private String reviewComment;

    private LocalDateTime reviewTime;
}
