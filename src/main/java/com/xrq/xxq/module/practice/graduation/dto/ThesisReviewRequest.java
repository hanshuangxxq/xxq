package com.xrq.xxq.module.practice.graduation.dto;

import com.xrq.xxq.module.practice.graduation.entity.ThesisStatusEnum;

import lombok.Data;

/**
 * 论文评审请求（教师/教务）。
 */
@Data
public class ThesisReviewRequest {

    private ThesisStatusEnum status;     // 仅允许 UNDER_REVIEW/PASSED/FAILED/REVISION
    private Integer reviewScore;
    private String reviewComment;
}
