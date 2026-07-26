package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;

import lombok.Data;

/**
 * 学生端看到的选课活动响应（活动即课程，聚合课程信息 + 选课组上下文 + 选课状态）。
 */
@Data
public class StudentCampaignResponse {
    private Long id;
    private String name;
    private Long semesterId;
    private String semesterName;
    private Integer startWeek;
    private Integer endWeek;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private CampaignStatusEnum status;

    // 课程字段（name 即课程名，已包含在上方）
    private Long courseId;
    private String courseCode;
    private Integer credit;
    private Integer courseHour;
    private String description;
    private String courseType;
    private List<Long> allowedGradeIds;
    private List<Long> allowedMajors;
    private List<Long> timeRestrictionIds;
    private Integer capacity;

    // 选课组上下文（组内选课上限跨活动共用）
    private Long groupId;
    private String groupName;
    private Integer groupMax;
    private Integer selectedInGroup;

    // 实时选课统计
    private Integer selectedCount;
    private Integer remaining;
    private Boolean selectedByMe;
}
