package com.xrq.xxq.module.coursework.assignment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xrq.xxq.module.coursework.common.MultiScoreRuleEnum;
import com.xrq.xxq.module.coursework.common.QuestionTypeEnum;
import lombok.Data;

/**
 * 作业题目快照：抽题/录入时从题库（或录入参数）完整复制，随作业删除应用层级联软删。
 * 发布后冻结（学生答案永远对着自己见到的题）。
 */
@Data
@TableName("course_assignment_question")
public class CourseAssignmentQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assignmentId;
    private Long sourceQuestionId;   // 溯源题库题（可空，仅展示用）
    private QuestionTypeEnum type;
    private String stem;
    private String optionsJson;
    private String answerJson;       // 标准答案快照
    private String analysis;
    private BigDecimal score;
    private Integer sortOrder;
    private MultiScoreRuleEnum scoreRule;
    private Integer caseSensitive;
    private Integer requireFile;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
