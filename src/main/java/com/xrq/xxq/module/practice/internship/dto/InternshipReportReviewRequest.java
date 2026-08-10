package com.xrq.xxq.module.practice.internship.dto;

import lombok.Data;

/**
 * 实习报告评审请求（院系管理者/教务）。
 */
@Data
public class InternshipReportReviewRequest {

    private Integer score;
    private String feedback;
}
