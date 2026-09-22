package com.xrq.xxq.module.coursework.assignment.dto;

import java.math.BigDecimal;

import lombok.Data;
import tools.jackson.databind.JsonNode;

/** 逐题作答视图：题目信息 + 学生答案 + 得分；standardAnswer/analysis 按 answer_visible 门控。 */
@Data
public class AnswerView {

    private Long answerId;
    private Long questionId;
    private String type;
    private String stem;
    private JsonNode options;
    private BigDecimal questionScore;
    private Integer sortOrder;
    private Boolean requireFile;
    private JsonNode myAnswer;
    private BigDecimal autoScore;
    private BigDecimal finalScore;
    private String comment;          // 逐题评语
    private JsonNode standardAnswer; // 门控
    private String analysis;         // 门控
}
