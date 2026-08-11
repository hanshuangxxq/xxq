package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.graduation.entity.AssignmentSourceEnum;

import lombok.Data;

/**
 * 师生匹配响应（R-6.12/R-6.13）。
 */
@Data
public class AssignmentResponse {

    private Long id;

    private Long campaignId;

    private Long studentId;

    private String studentName;

    private String studentNo;

    private Long teacherId;

    private String teacherName;

    private AssignmentSourceEnum source;

    private LocalDateTime assignTime;

    /** 改派前的原教师 user.id */
    private Long prevTeacherId;

    private String prevTeacherName;

    private String reassignReason;

    private LocalDateTime reassignTime;
}
