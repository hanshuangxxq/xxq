package com.xrq.xxq.module.analysis.dto;

import java.math.BigDecimal;

import com.xrq.xxq.module.analysis.entity.WarningLevelEnum;

import lombok.Data;

/**
 * 预警阈值配置视图。
 */
@Data
public class WarningConfigDto {

    private Long id;
    private WarningLevelEnum level;
    private BigDecimal gpaThreshold;       // GPA 下限（低于此值触发）
    private Integer failCountThreshold;    // 累计挂科数阈值（>=触发）
    private Integer semesterFailThreshold; // 单学期挂科数阈值（>=触发）
    private Integer enabled;               // 0:禁用 1:启用
}
