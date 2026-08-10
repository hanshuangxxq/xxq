package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 选题匹配记录（教师自选 / 院系分配 产生，绑定 student↔teacher↔proposal）。
 * <p>
 * student_id / teacher_id 均存 user.id；college_id 冗余（学生/教师所属院系），便于院系按院系查询。
 * 每学生每活动最多一条匹配（应用层查重）。教务最终审查通过后 status=APPROVED，导出送查重。
 */
@Data
@TableName("graduation_assignment")
public class GraduationAssignment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campaignId;
    private Long proposalId;
    private Long studentId;          // 学生 user.id
    private Long teacherId;          // 教师 user.id
    private Long collegeId;          // 院系 college.id（冗余）
    private AssignmentSourceEnum source;   // TEACHER_PICK/DEPT_ALLOCATE
    private AssignmentStatusEnum status;   // MATCHED/APPROVED/REJECTED
    private LocalDateTime assignTime;
    private LocalDateTime reviewTime;       // 教务最终审查时间
    private String reviewComment;

    @TableLogic
    private Integer deleted;
}
