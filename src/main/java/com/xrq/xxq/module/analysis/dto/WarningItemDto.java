package com.xrq.xxq.module.analysis.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xrq.xxq.module.analysis.entity.WarningLevelEnum;
import com.xrq.xxq.module.analysis.entity.WarningStatusEnum;

import lombok.Data;

/**
 * 预警看板条目：富化学生姓名/学号/班级/学期。
 */
@Data
public class WarningItemDto {

    private Long id;
    private Long studentUserId;
    private String studentName;
    private String studentNo;
    private String className;
    private WarningLevelEnum level;
    private String reason;
    private BigDecimal gpa;               // 扫描时 GPA 快照
    private Integer failCount;            // 扫描时累计挂科数
    private Integer semesterFailCount;    // 扫描时本学期挂科数
    private Long semesterId;
    private String semesterName;
    private WarningStatusEnum status;
    private LocalDateTime createTime;
}
