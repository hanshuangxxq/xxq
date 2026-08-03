package com.xrq.xxq.module.analysis.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 模板指标项：请求时只需 itemId/sortOrder/required（itemName/maxScore 由库快照）；
 * 响应时返回完整快照（itemName/maxScore）。
 */
@Data
public class TemplateItemDto {

    @NonNull
    private Long itemId;
    private String itemName;   // 响应返回快照；请求时忽略
    private Integer maxScore;  // 响应返回快照；请求时忽略
    private Integer sortOrder;
    private Integer required;  // 0:选填 1:必填，默认 1
}
