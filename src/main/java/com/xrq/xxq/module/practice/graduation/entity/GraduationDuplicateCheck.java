package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 论文查重记录（阶段三 R-8.5/R-8.6，第三方平台模式）。
 * <p>
 * 每次检测一行，历史保留（含不通过后的再次检测）。
 */
@Data
@TableName("graduation_duplicate_check")
public class GraduationDuplicateCheck {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 论文ID graduation_thesis.id */
    private Long thesisId;

    /** 活动ID graduation_campaign.id */
    private Long campaignId;

    /** 学生 user.id */
    private Long studentId;

    /** 重复率（百分比） */
    private Integer duplicateRate;

    /** 检测平台名称 */
    private String platform;

    /** 检测时间 */
    private LocalDateTime checkTime;

    private DuplicateResultEnum result;

    private String comment;

    /** 查重报告存储相对路径（objects/graduation-duplicate-report/...），可空 */
    private String fileName;

    /** 查重报告展示文件名 */
    private String fileOriginal;

    /** 登记人 user.id */
    private Long operatorId;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
