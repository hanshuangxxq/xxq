package com.xrq.xxq.module.practice.graduation.dto;

import com.xrq.xxq.module.practice.graduation.entity.AssignmentSourceEnum;
import com.xrq.xxq.module.practice.graduation.entity.ProposalStatusEnum;

import lombok.Data;

/**
 * 教师自由选择池行（Q-2：本活动参与年级 ∩ 本院系学生，含未选题者）。
 */
@Data
public class TeacherPickPoolRow {

    /** 学生 user.id */
    private Long studentId;

    private String studentNo;

    private String studentName;

    private String className;

    /** 题目名称（未选题为空） */
    private String proposalTitle;

    /** 主要内容说明（R-6.2 教师按题目匹配指导方向，未选题为空） */
    private String proposalContent;

    /** 选题状态（未选题为空） */
    private ProposalStatusEnum proposalStatus;

    /** 是否已被选择/分配 */
    private Boolean assigned;

    /** 匹配来源（未分配为空） */
    private AssignmentSourceEnum assignmentSource;
}
