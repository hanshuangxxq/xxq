package com.xrq.xxq.module.practice.graduation.dto;

import lombok.Data;

/**
 * 学生选题申请请求。
 */
@Data
public class SelectionApplyRequest {

    private Long topicId;
    private String applyReason;
}
