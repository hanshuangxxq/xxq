package com.xrq.xxq.module.coursework.assignment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 逐题作答：每份提交对每道题一行（未答题 answerJson=null）。
 * 客观题 autoScore=finalScore（提交即判）；ESSAY 待教师批改（finalScore=null）。
 * 重交时旧行软删、全量重写。
 */
@Data
@TableName("course_assignment_answer")
public class CourseAssignmentAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long submissionId;
    private Long questionId;         // 快照行 id
    private String answerJson;       // 学生答案（结构按题型）
    private BigDecimal autoScore;
    private BigDecimal finalScore;
    private String comment;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
