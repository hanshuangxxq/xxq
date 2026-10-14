package com.xrq.xxq.module.coursework.material.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 资料视图。下载走 file 模块 POST 通用下载，携带 fileName。 */
@Data
public class MaterialView {

    private Long id;
    private Long teachInfoId;
    private Long folderId; // 所在文件夹 id（null=根目录）
    private String title;
    private String description;
    private String fileName;
    private String fileOriginal;
    private String fileExt;
    private Long sizeBytes;
    private LocalDateTime createTime;
}
