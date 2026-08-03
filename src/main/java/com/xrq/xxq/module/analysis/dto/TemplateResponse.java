package com.xrq.xxq.module.analysis.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.xrq.xxq.module.analysis.entity.EvaluationTemplateStatusEnum;

import lombok.Data;

/**
 * 评教模板-返回视图（含指标列表）。
 */
@Data
public class TemplateResponse {

    private Long id;
    private String name;
    private String description;
    private EvaluationTemplateStatusEnum status;
    private Integer isDefault;
    private List<TemplateItemDto> items;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
