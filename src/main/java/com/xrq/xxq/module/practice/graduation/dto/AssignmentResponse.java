package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 选题匹配记录响应。
 */
@Data
public class AssignmentResponse {

    private Long id;
    private Long campaignId;
    private String campaignTitle;
    private Long proposalId;
    private String proposalTitle;
    private Long studentId;
    private String studentName;
    private String studentNo;
    private Long teacherId;
    private String teacherName;
    private String teacherNo;
    private Long collegeId;
    private String collegeName;
    private String source;          // TEACHER_PICK/DEPT_ALLOCATE
    private String status;          // MATCHED/APPROVED/REJECTED
    private LocalDateTime assignTime;
    private LocalDateTime reviewTime;
    private String reviewComment;
}
