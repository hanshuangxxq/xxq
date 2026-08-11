package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.xrq.xxq.module.practice.graduation.entity.ThesisStatusEnum;

import lombok.Data;

/**
 * 论文响应（含查重记录历史）。
 */
@Data
public class ThesisResponse {

    private Long id;

    private Long campaignId;

    private Long assignmentId;

    private Long studentId;

    private String studentName;

    private String title;

    /** 磁盘存储文件名（下载用） */
    private String fileName;

    /** 原始文件名 */
    private String fileOriginal;

    private Integer version;

    private Integer isLatest;

    private ThesisStatusEnum status;

    private LocalDateTime submitTime;

    private Long reviewTeacherId;

    private String reviewTeacherName;

    private String reviewComment;

    private LocalDateTime reviewTime;

    /** 查重记录（按时间正序） */
    private List<DuplicateCheckResponse> duplicateChecks;
}
