package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.graduation.entity.AssignmentSourceEnum;
import com.xrq.xxq.module.practice.graduation.entity.MidtermConclusionEnum;
import com.xrq.xxq.module.practice.graduation.entity.ProposalStatusEnum;

import lombok.Data;

/**
 * 教务/院系看板行（R-5.8：学号、姓名、院系、年级、选题状态、题目名称、指导教师、申请提交时间、审批完成时间）。
 */
@Data
public class DashboardRow {

    /** 学生 user.id */
    private Long studentId;

    private String studentNo;

    private String studentName;

    private String className;

    private Long collegeId;

    private String collegeName;

    private String gradeName;

    /** 选题申请ID（未提交为空） */
    private Long proposalId;

    /** 题目名称（未提交为空） */
    private String proposalTitle;

    /** 主要内容说明（未提交为空） */
    private String proposalContent;

    /** 选题状态（未提交为空） */
    private ProposalStatusEnum proposalStatus;

    /** 申请提交时间（未提交为空） */
    private LocalDateTime proposalSubmitTime;

    /** 审批完成时间=教务终审通过时间（未完成审批为空） */
    private LocalDateTime proposalApprovedTime;

    /** 师生匹配ID（未分配为空） */
    private Long assignmentId;

    /** 指导教师 user.id（未分配为空） */
    private Long teacherId;

    /** 指导教师姓名（未分配为空） */
    private String teacherName;

    /** 匹配来源（未分配为空） */
    private AssignmentSourceEnum assignmentSource;

    /** 中期检查ID（未提交为空） */
    private Long midtermId;

    /** 中期结论（R-7.5 教务看板预警，未评审为空） */
    private MidtermConclusionEnum midtermConclusion;
}
