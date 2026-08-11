package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.graduation.entity.GuidanceFormEnum;

import lombok.Data;

/**
 * 过程指导记录响应。
 */
@Data
public class GuidanceLogResponse {

    private Long id;

    private Long campaignId;

    private Long studentId;

    private String studentName;

    private LocalDateTime logTime;

    private GuidanceFormEnum form;

    private String summary;

    private LocalDateTime createTime;
}
