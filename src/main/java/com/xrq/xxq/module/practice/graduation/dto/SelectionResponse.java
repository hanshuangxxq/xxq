package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.graduation.entity.SelectionStatusEnum;

import lombok.Data;

@Data
public class SelectionResponse {

    private Long id;
    private Long topicId;
    private String topicTitle;
    private Long studentId;
    private String studentName;
    private Long teacherId;
    private String teacherName;
    private SelectionStatusEnum status;
    private String applyReason;
    private LocalDateTime selectTime;
    private LocalDateTime reviewTime;
    private String reviewComment;
}
