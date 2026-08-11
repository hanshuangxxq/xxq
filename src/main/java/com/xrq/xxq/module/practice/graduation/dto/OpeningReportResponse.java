package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.graduation.entity.OpeningReportStatusEnum;

import lombok.Data;

/**
 * 开题报告响应。
 */
@Data
public class OpeningReportResponse {

    private Long id;

    private Long campaignId;

    private Long assignmentId;

    private Long studentId;

    private String studentName;

    private String title;

    private String content;

    /** 原始文件名（有附件时） */
    private String fileOriginal;

    private OpeningReportStatusEnum status;

    private LocalDateTime submitTime;

    private Long reviewTeacherId;

    private String reviewTeacherName;

    private String reviewComment;

    private LocalDateTime reviewTime;
}
