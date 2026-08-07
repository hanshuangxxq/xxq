package com.xrq.xxq.module.scheduling.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 问题事实（不可变输入）：组合时间段时间段与星期几。
 * 每个 Timeslot = 某周几的某一个上课时间区间。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Timeslot {

    /** 唯一标识 = timeId * 10 + dayOfWeek（求解后反向拆解写回 teach_info） */
    private Long id;

    /** 星期几 */
    private DayOfWeek dayOfWeek;

    /** 开始时间（来自 time.start_period） */
    private LocalTime startTime;

    /** 结束时间（来自 time.end_period） */
    private LocalTime endTime;

    /**
     * 预留课程ID（null 表示无限制）。
     * 非 null 时此时间段仅供该常规课程使用（教务管理员设定）。
     */
    private Long reservedCourseId;

    /**
     * 预留公选课活动ID（null 表示无限制）。
     * 非 null 时此时间段仅供该公选课活动使用。
     */
    private Long reservedCampaignId;
}
