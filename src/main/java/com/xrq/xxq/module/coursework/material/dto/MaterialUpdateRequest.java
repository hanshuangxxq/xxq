package com.xrq.xxq.module.coursework.material.dto;

import lombok.Data;

/** 资料元数据修改（JSON；文件本体不可换，换文件 = 删除重传）。 */
@Data
public class MaterialUpdateRequest {

    private String title;
    private String description;
}
