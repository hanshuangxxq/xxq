package com.xrq.xxq.module.practice.internship.dto;

import lombok.Data;

/**
 * 实习报名审核请求（教师/教务）。
 */
@Data
public class InternshipReviewRequest {

    private Boolean approved;
    private String reviewComment;
}
