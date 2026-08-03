package com.xrq.xxq.module.analysis.dto;

import java.util.Map;

import lombok.Data;

/**
 * 预警扫描结果摘要。
 */
@Data
public class WarningScanResultDto {

    private Integer scannedCount;   // 扫描学生数
    private Integer warnedCount;    // 当前生效预警学生数（新激活）
    private Integer resolvedCount;  // 本次解除预警数
    private Map<String, Integer> byLevel; // 黄色预警/橙色预警/红色预警 -> 生效人数
}
