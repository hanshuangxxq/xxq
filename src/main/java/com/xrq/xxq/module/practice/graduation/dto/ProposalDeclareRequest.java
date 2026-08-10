package com.xrq.xxq.module.practice.graduation.dto;

import lombok.Data;

/**
 * 学生选题申报请求（学生自拟，不选教师）。
 */
@Data
public class ProposalDeclareRequest {

    private Long campaignId;       // 活动 id（必填）
    private String title;          // 选题标题（必填）
    private String description;    // 选题描述
    private String requirements;   // 选题要求
}
