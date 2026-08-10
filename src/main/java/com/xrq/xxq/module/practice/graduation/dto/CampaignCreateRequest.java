package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 选题活动创建请求（教务）。
 */
@Data
public class CampaignCreateRequest {

    private Long semesterId;             // 可空，默认当前学期
    private String title;                // 活动标题
    private LocalDateTime deadline;      // 申报截止时间
    private Integer supervisorCapacity;  // 每教师最大指导数（必填，统一）
    private Integer freeSelectCapacity;  // 每教师自选数（必填，≤supervisorCapacity，统一）
    private List<Long> allowedGradeIds;  // 允许参与的年级 id（空=不限）
}
