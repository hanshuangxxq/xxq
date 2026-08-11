package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.graduation.entity.DuplicateResultEnum;

import lombok.Data;

/**
 * 查重记录响应。
 */
@Data
public class DuplicateCheckResponse {

    private Long id;

    private Long thesisId;

    private Integer duplicateRate;

    private String platform;

    private LocalDateTime checkTime;

    private DuplicateResultEnum result;

    private String comment;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime createTime;
}
