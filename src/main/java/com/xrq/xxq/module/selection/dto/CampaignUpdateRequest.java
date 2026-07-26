package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.xrq.xxq.module.course.entity.CurseEnum;

import lombok.Data;

@Data
public class CampaignUpdateRequest {
    private String name;
    private Long semesterId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer startWeek;
    private Integer endWeek;
    /**
     * 选课组 ID。可选；非空且与当前绑定不同时触发换绑（要求 DRAFT 状态）。
     * 为 null 时不修改绑定关系（除非 {@link #unbindGroup} 为 true）。
     */
    private Long groupId;
    /**
     * 是否解绑当前选课组。为 true 时清除 campaign.group_id（要求 DRAFT 状态）。
     * 默认 false。与 groupId 互斥：若同时传 groupId 非 null 且 unbindGroup=true 将报错。
     */
    private Boolean unbindGroup;
    // 课程字段（全部可选，部分更新；同步写入衍生 course。name 即课程名）
    private String courseCode;
    private Integer credit;
    private Integer courseHour;
    private String description;
    private CurseEnum courseType;
    private List<Long> allowedGradeIds;
    private List<Long> allowedMajors;
    private List<Long> timeRestrictionIds;
    private Integer capacity;
}
