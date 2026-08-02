package com.xrq.xxq.module.score.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 成绩复核申请。
 * <p>
 * 状态流转：PENDING（学生提交）-> TEACHER_REPLIED（教师回复，可调分）
 * -> ESCALATED（学生升级到教务）-> RESOLVED/REJECTED（教务终审，可调分并锁定成绩）。
 */
@Data
@TableName("score_review")
public class ScoreReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scoreId;            // FK -> grade.id
    private Long studentUserId;      // 申请人 user.id
    private String reason;           // 申请理由
    private ReviewStatusEnum status; // PENDING/TEACHER_REPLIED/ESCALATED/RESOLVED/REJECTED
    private String teacherReply;     // 教师回复
    private Long teacherId;          // 处理教师 teacher.id
    private String adminReply;       // 教务回复
    private Long adminId;            // 处理教务 user.id
    private LocalDateTime escalateTime;
    private LocalDateTime resolvedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
