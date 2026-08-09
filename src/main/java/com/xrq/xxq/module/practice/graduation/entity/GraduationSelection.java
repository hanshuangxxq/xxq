package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 毕业设计选题申请记录（学生选题 + 教师审核）。
 * <p>
 * student_id / teacher_id 均存 user.id。teacher_id 冗余自选题，便于教师按指导关系查询。
 */
@Data
@TableName("graduation_selection")
public class GraduationSelection {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long topicId;
    private Long studentId;          // 学生 user.id
    private Long teacherId;          // 指导教师 user.id（冗余自选题）
    private SelectionStatusEnum status;
    private String applyReason;
    private LocalDateTime selectTime;
    private LocalDateTime reviewTime;
    private String reviewComment;

    @TableLogic
    private Integer deleted;
}
