package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 开题报告（阶段二 R-7.1/R-7.2）。
 * <p>
 * 学生提交（研究目标/内容/技术路线/进度安排）→ 指导教师审核（通过/退回修改）。
 * 退回（REVISION）后可修改重提，回到 SUBMITTED。
 */
@Data
@TableName("graduation_opening_report")
public class GraduationOpeningReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动ID graduation_campaign.id */
    private Long campaignId;

    /** 师生匹配ID graduation_assignment.id */
    private Long assignmentId;

    /** 学生 user.id */
    private Long studentId;

    /** 开题报告标题 */
    private String title;

    /** 研究目标/内容/技术路线/进度安排 */
    private String content;

    /** 磁盘存储文件名（UUID） */
    private String fileName;

    /** 原始文件名 */
    private String fileOriginal;

    private OpeningReportStatusEnum status;

    /** （最近一次）提交时间 */
    private LocalDateTime submitTime;

    /** 审核教师 user.id */
    private Long reviewTeacherId;

    private String reviewComment;

    private LocalDateTime reviewTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
