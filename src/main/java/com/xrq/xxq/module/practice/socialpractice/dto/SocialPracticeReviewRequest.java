package com.xrq.xxq.module.practice.socialpractice.dto;

import lombok.Data;

/**
 * 社会实践申报审核请求（教务）。
 */
@Data
public class SocialPracticeReviewRequest {

    private Boolean approved;
    private String reviewComment;
}
