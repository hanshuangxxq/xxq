package com.xrq.xxq.module.practice.internship.dto;

import lombok.Data;

/**
 * 实习报告评审请求（教师/教务）。
 */
@Data
public class InternshipReportReviewRequest {

    private Integer score;
    private String feedback;
}
