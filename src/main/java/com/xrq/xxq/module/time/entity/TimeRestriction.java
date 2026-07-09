package com.xrq.xxq.module.time.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 时段限制规则（由教务管理员制定）。
 * <p>
 * BLOCKED  — 该时段完全禁止排课（如固定活动时间）
 * RESERVED — 该时段预留给特定课程统一上课（如全校政治课）
 */
@Data
@TableName("time_restriction")
public class TimeRestriction {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** FK → time.id */
    private Long timeId;

    /** 星期几 (1=周一 ~ 7=周日) */
    private Integer dayOfWeek;

    /** 限制类型：BLOCKED / RESERVED */
    private String restrictionType;

    /** FK → course.id（仅 RESERVED 类型有效，用于指定预留的课程） */
    private Long courseId;

    /** 限制原因说明 */
    private String reason;
}
