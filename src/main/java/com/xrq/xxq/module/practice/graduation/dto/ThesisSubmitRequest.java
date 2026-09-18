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

    /** 分片上传产物路径（objects/...），与 multipart 的 file 部分二选一；大文件走这里。 */
    private String filePath;

    /** 展示文件名，配合 filePath 使用；留空则回退产物文件名。 */
    private String fileOriginal;
}
