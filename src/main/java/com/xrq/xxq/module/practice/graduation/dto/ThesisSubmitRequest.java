package com.xrq.xxq.module.practice.graduation.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 学生提交/重提论文（R-8.1，文件随 multipart 上传）。
 */
@Data
public class ThesisSubmitRequest {

    /** 活动ID */
    @NonNull
    private Long campaignId;

    /** 论文题目 */
    @NonNull
    private String title;
}
