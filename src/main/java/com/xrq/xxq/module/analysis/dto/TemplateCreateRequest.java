package com.xrq.xxq.module.analysis.dto;

import java.util.List;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 评教模板-创建请求。
 */
@Data
public class TemplateCreateRequest {

    @NonNull
    private String name;
    private String description;
    @NonNull
    private List<TemplateItemDto> items;
}
