package com.xrq.xxq.module.analysis.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 学业预警阈值配置：每级别一行，任一条件命中即触发该级别。
 * <p>GPA 下限（低于触发）、累计挂科阈值、单学期挂科阈值。
 */
@Data
@TableName("warning_config")
public class WarningConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private WarningLevelEnum level;
    private BigDecimal gpaThreshold;       // GPA 下限（低于此值触发）
    private Integer failCountThreshold;    // 累计挂科数阈值（>=触发）
    private Integer semesterFailThreshold; // 单学期挂科数阈值（>=触发）
    private Integer enabled;               // 0:禁用 1:启用
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
