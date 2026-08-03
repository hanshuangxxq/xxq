package com.xrq.xxq.module.analysis.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 评教指标库-创建请求。
 */
@Data
public class ItemCreateRequest {

    @NonNull
    private String name;
    private String description;
    private Integer maxScore;  // 满分，默认 5
}
