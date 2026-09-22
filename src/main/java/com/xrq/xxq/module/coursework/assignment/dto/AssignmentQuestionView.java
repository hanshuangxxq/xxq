package com.xrq.xxq.module.coursework.assignment.dto;

import java.math.BigDecimal;

import lombok.Data;
import tools.jackson.databind.JsonNode;

/**
 * 作业题目视图。
 * 教师端恒含标准答案/解析；学生端作答页不含，my-submission 按 answer_visible 门控附带。
 * myAnswer 仅学生端作答页使用（未提交时来自 Redis 草稿）。
 */
@Data
public class AssignmentQuestionView {

    private Long id;
    private String type;            // QuestionTypeEnum.code，前端按 code 分发题型组件
    private String stem;
    private JsonNode options;
    private BigDecimal score;
    private Integer sortOrder;
    private String scoreRule;       // 多选计分规则 code
    private Boolean caseSensitive;  // 填空
    private Boolean requireFile;    // 大题
    private JsonNode answer;        // 标准答案（按可见性）
    private String analysis;        // 解析（按可见性）
    private JsonNode myAnswer;      // 学生端草稿答案（未提交时）
}
