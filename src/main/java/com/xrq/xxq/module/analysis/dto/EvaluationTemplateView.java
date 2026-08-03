package com.xrq.xxq.module.analysis.dto;

import java.util.List;

import lombok.Data;

/**
 * 学生评教表单视图：解析所得模板（课程覆盖优先，否则全局默认）+ 指标列表。
 */
@Data
public class EvaluationTemplateView {

    private Long templateId;
    private String templateName;
    private List<TemplateItemDto> items;
}
