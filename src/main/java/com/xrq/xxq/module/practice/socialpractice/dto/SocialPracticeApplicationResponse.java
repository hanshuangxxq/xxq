package com.xrq.xxq.module.practice.socialpractice.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.common.entity.AuditStatusEnum;

import lombok.Data;

@Data
public class SocialPracticeApplicationResponse {

    private Long id;
    private Long practiceId;
    private String practiceTitle;
    private Long studentId;
    private String studentName;
    private String teamName;
    private String members;
    private AuditStatusEnum status;
    private String applyReason;
    private LocalDateTime applyTime;
    private LocalDateTime reviewTime;
    private String reviewComment;
}
