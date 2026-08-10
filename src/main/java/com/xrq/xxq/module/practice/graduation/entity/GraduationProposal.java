package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 学生选题申报实体（学生自拟选题，不选教师）。
 * <p>
 * student_id 存学生 user.id。申报后由院系管理者初审（仅本学院），通过后进入匹配池。
 */
@Data
@TableName("graduation_proposal")
public class GraduationProposal {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campaignId;
    private Long studentId;          // 学生 user.id
    private String title;
    private String description;
    private String requirements;     // 选题要求
    private ProposalStatusEnum status;   // PENDING_DEPT/DEPT_APPROVED/DEPT_REJECTED/ASSIGNED
    private Long deptReviewerId;         // 院系初审人 user.id
    private LocalDateTime deptReviewTime;
    private String deptReviewComment;
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
