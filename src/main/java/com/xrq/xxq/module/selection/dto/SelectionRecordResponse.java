package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.selection.entity.RecordStatusEnum;

import lombok.Data;

@Data
public class SelectionRecordResponse {
    private Long id;
    private Long campaignId;
    private Long courseId;
    private String courseName;
    private String courseCode;
    private Integer credit;
    private String courseType;
    private RecordStatusEnum status;
    private LocalDateTime selectTime;
    private LocalDateTime dropTime;
}
