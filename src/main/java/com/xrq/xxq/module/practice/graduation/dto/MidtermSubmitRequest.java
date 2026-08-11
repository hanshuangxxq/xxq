package com.xrq.xxq.module.practice.graduation.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 学生提交中期检查材料（R-7.4，文件随 multipart 上传）。
 */
@Data
public class MidtermSubmitRequest {

    /** 活动ID */
    @NonNull
    private Long campaignId;

    /** 进展情况/已完成工作/后续计划 */
    @NonNull
    private String content;
}
