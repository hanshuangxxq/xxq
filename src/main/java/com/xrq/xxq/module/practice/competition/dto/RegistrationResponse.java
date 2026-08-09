package com.xrq.xxq.module.practice.competition.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.common.entity.AuditStatusEnum;

import lombok.Data;

@Data
public class RegistrationResponse {

    private Long id;
    private Long competitionId;
    private String competitionName;
    private Long studentId;
    private String studentName;
    private String teamName;
    private String members;
    private AuditStatusEnum status;
    private LocalDateTime registerTime;
    private LocalDateTime reviewTime;
    private String reviewComment;
}
