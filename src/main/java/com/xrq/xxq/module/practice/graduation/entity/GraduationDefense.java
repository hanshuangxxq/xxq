package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 答辩安排（阶段四 R-9.1）。
 * <p>
 * 院系管理者为本院系学生安排答辩（分组/时间/地点/评阅教师/答辩教师组），
 * 仅查重通过（DUPLICATE_PASSED）的学生可被安排。
 */
@Data
@TableName("graduation_defense")
public class GraduationDefense {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动ID graduation_campaign.id */
    private Long campaignId;

    /** 学生 user.id */
    private Long studentId;

    /** 答辩分组名称 */
    private String groupName;

    private LocalDateTime defenseTime;

    /** 答辩地点 */
    private String location;

    /** 评阅教师 user.id */
    private Long reviewerId;

    /** 答辩教师 user.id 逗号分隔 */
    private String defenseTeacherIds;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
