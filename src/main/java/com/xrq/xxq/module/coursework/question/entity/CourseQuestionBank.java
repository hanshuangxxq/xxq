package com.xrq.xxq.module.coursework.question.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xrq.xxq.module.coursework.common.MultiScoreRuleEnum;
import com.xrq.xxq.module.coursework.common.QuestionTypeEnum;
import lombok.Data;

/**
 * 题库题目（教师个人题库，owner_teacher_id = user.id）。
 * 作业抽题后完整快照进 course_assignment_question —— 本表后续改动不影响已发布作业。
 */
@Data
@TableName("course_question_bank")
public class CourseQuestionBank {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerTeacherId;
    private QuestionTypeEnum type;
    private String stem;
    // updateStrategy=ALWAYS：更新时允许把下列列改回 null（清空解析/选项/答案/计分规则/课程标签），
    // 否则 MyBatis Plus updateById 默认跳过 null 字段，改题型后旧值残留
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long courseId;           // 课程标签（与 campaignId 二选一）
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long campaignId;         // 公选活动标签
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String optionsJson;      // [{"key":"A","text":"..."}]
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String answerJson;       // 标准答案（结构按题型）
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String analysis;
    private BigDecimal defaultScore;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private MultiScoreRuleEnum scoreRule;
    private Integer caseSensitive;   // 填空：1=大小写敏感
    private Integer requireFile;     // 大题：1=必须传附件
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
