package com.xrq.xxq.module.practice.socialpractice.dto;

import lombok.Data;

/**
 * 社会实践报告评审请求（教务）。
 */
@Data
public class SocialPracticeReportReviewRequest {

    private Integer score;
    private String feedback;
}
