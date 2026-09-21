package com.xrq.xxq.module.coursework.assignment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xrq.xxq.module.coursework.assignment.entity.AssignmentStatusEnum;
import lombok.Data;

/** 作业视图：教师视角带提交统计，学生视角带「我的提交」摘要。 */
@Data
public class AssignmentView {

    private Long id;
    private Long teachInfoId;
    private String title;
    private String content;
    private String fileName;
    private String fileOriginal;
    private LocalDateTime deadline;
    private BigDecimal totalScore;
    private AssignmentStatusEnum status;
    private Long teacherId;
    private LocalDateTime createTime;

    // ---- 教师视角统计 ----
    private Integer submittedCount;
    private Integer gradedCount;

    // ---- 学生视角我的提交摘要 ----
    private Long mySubmissionId;
    private String mySubmissionStatus;
    private BigDecimal myScore;
    private Integer myLate;
    private Integer myVersion;
}
