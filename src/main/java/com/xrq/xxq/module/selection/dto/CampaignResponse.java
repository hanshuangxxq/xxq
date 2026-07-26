package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;

import lombok.Data;

@Data
public class CampaignResponse {
    private Long id;
    private String name;
    private Long semesterId;
    private String semesterName;
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
    private String courseType;
    private List<Long> allowedGradeIds;
    private List<Long> allowedMajors;
    private List<Long> timeRestrictionIds;
    private Integer capacity;
    // 选课组绑定信息
    private Long groupId;
    private String groupName;
    private LocalDateTime createTime;
    /**
     * 该活动当前绑定的选课组 ID。
     * 仅在 {@code listBindableForGroup} 等需要暴露绑定信息的场景填充，
     * 其它接口（如 listAll / getDetail）保持为 null。
     */
    private Long boundGroupId;
}
