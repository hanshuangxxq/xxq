package com.xrq.xxq.module.selection.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xrq.xxq.module.course.entity.CurseEnum;

import lombok.Data;

/**
 * 选课活动实体。
 * <p>
 * 活动对应一门公选课，课程信息（名称/编号/学分/学时/描述/类型）直接存在本表，
 * 不再在 course 表生成衍生记录。下游表（teach_info/score/exam/time_restriction/
 * teaching_evaluation）通过 {@code campaign_id} 引用本活动，富化时按 campaign_id
 * 查本表获取课程字段。
 * {@code allowedGradeIds} / {@code allowedMajors} 为空表示不限；非空时按 id 列表过滤。
 */
@Data
@TableName("selection_campaign")
public class SelectionCampaign {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long semesterId;
    // 课程字段（活动即公选课，不再走 course 表）
    private String courseName;
    private String courseCode;
    private Integer credit;
    private Integer courseHour;
    private String description;
    private CurseEnum courseType;
    private Integer startWeek;
    private Integer endWeek;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private CampaignStatusEnum status;
    private String allowedGradeIds;
    private String allowedMajors;
    private Integer capacity;
    // 选课组绑定（一个活动只能绑定一个组）
    private Long groupId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
