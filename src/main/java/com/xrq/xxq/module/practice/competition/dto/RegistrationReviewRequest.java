package com.xrq.xxq.module.practice.competition.dto;

import lombok.Data;

/**
 * 竞赛报名审核请求（教务）。
 */
@Data
public class RegistrationReviewRequest {

    private Boolean approved;
    private String reviewComment;
}
