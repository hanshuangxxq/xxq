package com.xrq.xxq.module.practice.graduation.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 学生提交/重提开题报告（R-7.1，文件随 multipart 上传）。
 */
@Data
public class OpeningReportSubmitRequest {

    /** 活动ID */
    @NonNull
    private Long campaignId;

    /** 开题报告标题 */
    @NonNull
    private String title;

    /** 研究目标/内容/技术路线/进度安排 */
    @NonNull
    private String content;

    /** 分片上传产物路径（objects/...），与 multipart 的 file 部分二选一；大文件走这里。 */
    private String filePath;

    /** 展示文件名，配合 filePath 使用；留空则回退产物文件名。 */
    private String fileOriginal;
}
