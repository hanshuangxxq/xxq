package com.xrq.xxq.module.practice.socialpractice.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.socialpractice.entity.SocialPracticeStatusEnum;

import lombok.Data;

@Data
public class SocialPracticeResponse {

    private Long id;
    private Long semesterId;
    private String title;
    private String description;
    private String organizer;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
    private Integer selectedCount;
    private SocialPracticeStatusEnum status;
    private LocalDateTime createTime;
}
