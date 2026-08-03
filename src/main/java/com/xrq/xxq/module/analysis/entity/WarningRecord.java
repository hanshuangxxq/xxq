package com.xrq.xxq.module.analysis.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 学业预警记录：按 (student_user_id, semester_id, level) 唯一，扫描时 upsert。
 * <p>status ACTIVE/RESOLVED 记录预警生命周期。
 */
@Data
@TableName("warning_record")
public class WarningRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentUserId;
    private Long semesterId;
    private WarningLevelEnum level;
    private String reason;                 // 触发原因描述
    private BigDecimal gpaSnapshot;        // 扫描时 GPA 快照
    private Integer failCount;             // 扫描时累计挂科数
    private Integer semesterFailCount;     // 扫描时本学期挂科数
    private WarningStatusEnum status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
