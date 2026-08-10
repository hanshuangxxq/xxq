package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 选题申报响应（含匹配信息：教师/匹配来源/匹配状态）。
 */
@Data
public class ProposalResponse {

    private Long id;
    private Long campaignId;
    private String campaignTitle;
    private Long studentId;
    private String studentName;
    private String studentNo;
    private Long collegeId;
    private String collegeName;
    private String title;
    private String description;
    private String requirements;
    private String status;              // ProposalStatusEnum
    private String deptReviewComment;
    private LocalDateTime deptReviewTime;
    private LocalDateTime createTime;

    // 匹配信息（已匹配时富化）
    private Long assignmentId;
    private Long teacherId;
    private String teacherName;
    private String teacherNo;
    private String assignmentSource;    // TEACHER_PICK/DEPT_ALLOCATE
    private String assignmentStatus;    // MATCHED/APPROVED/REJECTED
}
