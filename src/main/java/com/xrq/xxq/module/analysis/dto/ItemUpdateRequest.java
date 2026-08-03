package com.xrq.xxq.module.analysis.dto;

import lombok.Data;

/**
 * 评教指标库-更新请求。
 * <p>{@code updateTemplates=true} 时同步把变更刷新到引用本指标的模板快照（影响后续新提交）；
 * 为 false 或不传则模板保留旧快照；已提交的评教明细永不受影响。
 */
@Data
public class ItemUpdateRequest {

    private String name;
    private String description;
    private Integer maxScore;
    /** 是否同步变更到引用本指标的模板快照。 */
    private Boolean updateTemplates;
}
