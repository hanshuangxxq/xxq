package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.graduation.entity.TopicStatusEnum;

import lombok.Data;

@Data
public class TopicResponse {

    private Long id;
    private Long semesterId;
    private Long teacherId;
    private String teacherName;
    private String title;
    private String description;
    private String requirements;
    private Integer capacity;
    private Integer selectedCount;
    private TopicStatusEnum status;
    private LocalDateTime createTime;
}
