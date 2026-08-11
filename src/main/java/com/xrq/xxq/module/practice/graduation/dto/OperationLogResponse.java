package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 操作日志响应（R-10.4）。
 */
@Data
public class OperationLogResponse {

    private Long id;

    private Long campaignId;

    private Long operatorId;

    private String operatorName;

    private String operatorType;

    private String action;

    private String targetType;

    private Long targetId;

    private String detail;

    private LocalDateTime createTime;
}
