package com.xrq.xxq.module.practice.graduation.dto;

import lombok.Data;

/**
 * 毕业设计选题创建请求（教师发布）。
 */
@Data
public class TopicCreateRequest {

    private Long semesterId;      // 可空，默认当前学期
    private String title;
    private String description;
    private String requirements;
    private Integer capacity;
}
