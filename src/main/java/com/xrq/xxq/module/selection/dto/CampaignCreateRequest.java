package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.NonNull;

import com.xrq.xxq.module.course.entity.CurseEnum;

import lombok.Data;

@Data
public class CampaignCreateRequest {
    @NonNull
    private String name;
    @NonNull
    private Long semesterId;
    @NonNull
    private LocalDateTime startTime;
    @NonNull
    private LocalDateTime endTime;
    private Integer startWeek;
    private Integer endWeek;
    /**
     * 选课组 ID。可选；非空时在创建活动的同时绑定到该组（直接写入 campaign.group_id）。
     */
    private Long groupId;
    // 课程字段（活动即课程，name 同时作为活动名与课程名）
    @NonNull
    private String courseCode;
    @NonNull
    private Integer credit;
    private Integer courseHour;
    private String description;
    private CurseEnum courseType;
    private List<Long> allowedGradeIds;
    private List<Long> allowedMajors;
    private List<Long> timeRestrictionIds;
    @NonNull
    private Integer capacity;
}
