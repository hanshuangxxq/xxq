package com.xrq.xxq.module.analysis.dto;

import lombok.Data;

/**
 * 已提交评教的指标得分视图（含快照指标名/满分）。
 */
@Data
public class EvaluationScoreView {

    private Long itemId;
    private String itemName;
    private Integer maxScore;
    private Integer score;
}
