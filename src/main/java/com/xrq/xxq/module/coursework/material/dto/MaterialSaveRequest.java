package com.xrq.xxq.module.coursework.material.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 资料上传请求（multipart 的 data 部分）。 */
@Data
public class MaterialSaveRequest {

    @NonNull
    private Long teachInfoId;

    @NonNull
    private String title;

    private String description;

    /** 分片产物路径（与 multipart file 二选一）。 */
    private String filePath;

    /** 原始文件名（可空）。 */
    private String fileOriginal;
}
