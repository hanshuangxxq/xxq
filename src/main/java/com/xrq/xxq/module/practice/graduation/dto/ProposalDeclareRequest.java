package com.xrq.xxq.module.practice.graduation.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 学生提交/重提选题申请（R-5.2）。
 */
@Data
public class ProposalDeclareRequest {

    /** 活动ID */
    @NonNull
    private Long campaignId;

    /** 自拟题目名称 */
    @NonNull
    private String title;

    /** 主要内容说明（不少于100字） */
    @NonNull
    private String content;
}
