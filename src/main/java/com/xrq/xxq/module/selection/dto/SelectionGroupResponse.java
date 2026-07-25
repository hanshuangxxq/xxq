package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 选课组响应。
 * <p>
 * courseCount 为跨所有活动合计的课程数；boundCampaignCount 为绑定活动数。
 */
@Data
public class SelectionGroupResponse {
    private Long id;
    private String name;
    private Integer maxCourses;
    private Integer courseCount;
    private Integer boundCampaignCount;
    private LocalDateTime createTime;
}
