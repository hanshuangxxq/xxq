package com.xrq.xxq.module.analysis.dto;

import lombok.Data;

/**
 * 课程级模板覆盖请求：templateId 为 null 表示清除覆盖（回退到全局默认模板）。
 */
@Data
public class TemplateOverrideRequest {

    private Long templateId;
}
