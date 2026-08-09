package com.xrq.xxq.module.practice.graduation.dto;

import lombok.Data;

/**
 * 教师审核选题申请请求。
 */
@Data
public class SelectionReviewRequest {

    private Boolean approved;       // true 通过 / false 驳回
    private String reviewComment;
}
