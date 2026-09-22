package com.xrq.xxq.module.coursework.question.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xrq.xxq.module.coursework.common.QuestionTypeEnum;

import lombok.Data;
import tools.jackson.databind.JsonNode;

/** 题库题目视图（教师端，含标准答案）。 */
@Data
public class QuestionView {

    private Long id;
    private Long courseId;
    private Long campaignId;
    private QuestionTypeEnum type;
    private String stem;
    private JsonNode options;
    private JsonNode answer;
    private String analysis;
    private BigDecimal defaultScore;
    private String scoreRule;
    private Boolean caseSensitive;
    private Boolean requireFile;
    private LocalDateTime createTime;
}
