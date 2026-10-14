package com.xrq.xxq.module.coursework.material.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 文件夹改名请求（教师；文件夹不支持换父级）。 */
@Data
public class MaterialFolderRenameRequest {

    @NonNull
    private String title;
}
