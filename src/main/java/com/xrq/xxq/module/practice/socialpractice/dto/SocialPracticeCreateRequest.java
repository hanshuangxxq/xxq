package com.xrq.xxq.module.practice.socialpractice.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 社会实践项目创建请求（教务发布）。
 */
@Data
public class SocialPracticeCreateRequest {

    private Long semesterId;             // 可空，默认当前学期
    private String title;
    private String description;
    private String organizer;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
}
