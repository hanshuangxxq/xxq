package com.xrq.xxq.module.analysis.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 评教指标库-返回视图。
 */
@Data
public class ItemResponse {

    private Long id;
    private String name;
    private String description;
    private Integer maxScore;
    private Integer usedCount;  // 被多少模板引用
    private LocalDateTime createTime;
}
