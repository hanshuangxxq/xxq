package com.xrq.xxq.module.practice.internship.dto;

import lombok.Data;

/**
 * 实习报名审核请求（院系管理者/教务）。
 */
@Data
public class InternshipReviewRequest {

    private Boolean approved;
    private String reviewComment;
}
