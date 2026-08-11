package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 过程指导记录（阶段二 R-7.7，教师指导日志）。
 */
@Data
@TableName("graduation_guidance_log")
public class GraduationGuidanceLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动ID graduation_campaign.id */
    private Long campaignId;

    /** 师生匹配ID graduation_assignment.id */
    private Long assignmentId;

    /** 指导教师 user.id */
    private Long teacherId;

    /** 学生 user.id */
    private Long studentId;

    /** 指导时间 */
    private LocalDateTime logTime;

    private GuidanceFormEnum form;

    /** 指导内容摘要 */
    private String summary;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
