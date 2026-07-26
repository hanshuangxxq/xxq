package com.xrq.xxq.module.selection.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xrq.xxq.module.course.entity.CurseEnum;

import lombok.Data;

/**
 * 选课活动实体（= 一门可选课程）。
 * <p>
 * 活动本身就是一门课程，{@code name} 同时作为活动名称与课程名称（单一字段）。
 * 创建时自动在 course 表生成一条衍生记录（source = SELECTION_CAMPAIGN）用于排课系统
 * 识别并关联 TimeRestriction。{@code allowedGradeIds} / {@code allowedMajors}
 * 为空表示不限；非空时按 id 列表过滤。
 */
@Data
@TableName("selection_campaign")
public class SelectionCampaign {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long semesterId;
    private Long courseId;
    private Integer startWeek;
    private Integer endWeek;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private CampaignStatusEnum status;
    // 课程字段（name 即课程名，已包含在上方）
    private String courseCode;
    private Integer credit;
    private Integer courseHour;
    private String description;
    private CurseEnum courseType;
    private String allowedGradeIds;
    private String allowedMajors;
    private Integer capacity;
    // 选课组绑定（一个活动只能绑定一个组）
    private Long groupId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
