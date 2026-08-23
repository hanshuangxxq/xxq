package com.xrq.xxq.module.practice.graduation.dto;

import lombok.Data;

/**
 * 分配总览行（R-6.14 教务看板：每教师已选数/指定数/空缺席位）。
 */
@Data
public class AssignmentOverviewRow {

    /** 教师 user.id */
    private Long teacherId;

    private String teacherName;

    private String teacherNo;

    /** 已自由选择数 */
    private Long pickedCount;

    /** 已被指定分配数 */
    private Long allocatedCount;

    /** 可分配上限（活动配置） */
    private Integer capacity;

    /** 空缺席位数 = capacity - pickedCount - allocatedCount */
    private Long freeCount;
}
