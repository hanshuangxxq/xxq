package com.xrq.xxq.module.coursework.material.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 建文件夹请求(教师)。parentId 空 = 根层。 */
@Data
public class MaterialFolderSaveRequest {

    @NonNull
    private Long teachInfoId;

    /** 父文件夹 id;空 = 根层文件夹 */
    private Long parentId;

    @NonNull
    private String title;
}
