package com.xrq.xxq.module.practice.internship.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.common.entity.ReportStatusEnum;

import lombok.Data;

@Data
public class InternshipReportResponse {

    private Long id;
    private Long internshipId;
    private String internshipTitle;
    private Long studentId;
    private String studentName;
    private String title;
    private String summary;
    private String fileOriginal;
    private LocalDateTime submitTime;
    private Integer score;
    private String feedback;
    private LocalDateTime reviewTime;
    private ReportStatusEnum status;
}
