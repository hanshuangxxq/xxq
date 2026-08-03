package com.xrq.xxq.module.analysis.dto;

import java.util.List;

import lombok.Data;

/**
 * 评教模板-更新请求。items 传入则整体替换模板指标；不传则只改 name/description。
 */
@Data
public class TemplateUpdateRequest {

    private String name;
    private String description;
    private List<TemplateItemDto> items;
}
