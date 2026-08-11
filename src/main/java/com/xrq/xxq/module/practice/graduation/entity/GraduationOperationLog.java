package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 毕业设计操作日志（R-10.4：审批/分配/改派/导出/查重登记/成绩发布等关键动作留痕）。
 */
@Data
@TableName("graduation_operation_log")
public class GraduationOperationLog {

    @TableId(type = IdType.AUTO)
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
