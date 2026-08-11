package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 师生匹配（一名学生仅一条有效匹配，R-6.4）。
 * <p>
 * 来源：教师自选 / 院系指定。选题截止前教师可放弃（物理删除释放回池，R-6.6）；
 * 截止后院系管理者可改派（保留原教师与原因留痕，R-6.13）。
 */
@Data
@TableName("graduation_assignment")
public class GraduationAssignment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动ID graduation_campaign.id */
    private Long campaignId;

    /** 学生 user.id */
    private Long studentId;

    /** 指导教师 user.id */
    private Long teacherId;

    private AssignmentSourceEnum source;

    /** 匹配/分配时间 */
    private LocalDateTime assignTime;

    /** 改派前的原教师 user.id */
    private Long prevTeacherId;

    /** 改派原因 */
    private String reassignReason;

    /** 改派操作人 user.id */
    private Long reassignBy;

    /** 改派时间 */
    private LocalDateTime reassignTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
