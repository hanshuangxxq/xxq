package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.graduation.entity.ThesisStatusEnum;

import lombok.Data;

@Data
public class ThesisResponse {

    private Long id;
    private Long assignmentId;
    private Long studentId;
    private String studentName;
    private Long teacherId;
    private String teacherName;
    private String title;
    private String abstractText;
    private String fileOriginal;
    private LocalDateTime submitTime;
    private ThesisStatusEnum status;
    private Integer reviewScore;
    private String reviewComment;
    private LocalDateTime reviewTime;
}
