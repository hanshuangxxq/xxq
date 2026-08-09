package com.xrq.xxq.module.practice.internship.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.common.entity.AuditStatusEnum;

import lombok.Data;

@Data
public class InternshipApplicationResponse {

    private Long id;
    private Long internshipId;
    private String internshipTitle;
    private Long studentId;
    private String studentName;
    private AuditStatusEnum status;
    private String applyReason;
    private LocalDateTime applyTime;
    private LocalDateTime reviewTime;
    private String reviewComment;
}
