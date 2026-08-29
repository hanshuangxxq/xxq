package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 毕业设计操作日志记录模型（R-10.4：审批/分配/改派/导出/查重登记/成绩发布等关键动作留痕）。
 * <p>
 * 不落数据库：由 {@code GraduationLogServiceImpl} 以 JSONL 形式追加写入
 * {@code practice.operation-log-path} 目录下按活动划分的日志文件，一行一条。
 */
@Data
public class GraduationOperationLog {

    /** 全局单调递增 id（应用层分配，排序语义同原 DB 自增主键） */
    private Long id;

    /** 活动ID graduation_campaign.id */
    private Long campaignId;

    /** 操作人 user.id */
    private Long operatorId;

    /** 操作人角色 */
    private String operatorType;

    /** 动作描述（如 导出看板/改派学生） */
    private String action;

    /** 操作对象类型 */
    private String targetType;

    /** 操作对象ID */
    private Long targetId;

    /** 操作详情备注 */
    private String detail;

    private LocalDateTime createTime;
}
