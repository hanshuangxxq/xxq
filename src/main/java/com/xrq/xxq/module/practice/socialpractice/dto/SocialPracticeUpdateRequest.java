package com.xrq.xxq.module.practice.socialpractice.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 社会实践项目更新请求（部分更新，字段可空）。
 */
@Data
public class SocialPracticeUpdateRequest {

    private String title;
    private String description;
    private String organizer;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
}
