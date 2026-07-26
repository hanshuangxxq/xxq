package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 选课组响应。
 * <p>
 * campaignCount 为绑定到该组的选课活动数（一个活动即一门课程，故也是组内课程数）。
 */
@Data
public class SelectionGroupResponse {
    private Long id;
    private String name;
    private Integer maxCourses;
    private Integer campaignCount;
    private LocalDateTime createTime;
}
