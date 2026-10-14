package com.xrq.xxq.module.coursework.material.dto;

import lombok.Data;

/** 移动资料请求：folderId 可空，null/缺失 = 移回根目录。 */
@Data
public class MaterialMoveRequest {

    private Long folderId;
}
