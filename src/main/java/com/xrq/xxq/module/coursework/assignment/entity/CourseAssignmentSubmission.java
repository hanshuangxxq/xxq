package com.xrq.xxq.module.coursework.assignment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 作业提交：(assignment_id, student_id) 应用层唯一（查有则改 upsert）。
 * 截止前可重交覆盖（version+1，状态重置 SUBMITTED，清空已批改痕迹）。
 */
@Data
@TableName("course_assignment_submission")
public class CourseAssignmentSubmission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assignmentId;
    private Long studentId;          // 学生 user.id
    private String content;          // 文本作答
    private String fileName;         // 附件 objects/ 相对路径
    private String fileOriginal;     // 附件展示名
    private LocalDateTime submitTime;
    private Integer late;            // 迟交标记：1=迟交
    private SubmissionStatusEnum status;
    private BigDecimal score;
    private BigDecimal autoScore;    // 客观题自动得分合计
    private String comment;          // 评语
    private LocalDateTime gradeTime;
    private Integer version;         // 第几次提交
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
