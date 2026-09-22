package com.xrq.xxq.module.coursework.question.dto;

import java.math.BigDecimal;
import java.util.List;

import com.xrq.xxq.module.coursework.common.MultiScoreRuleEnum;
import com.xrq.xxq.module.coursework.common.OptionItem;
import com.xrq.xxq.module.coursework.common.QuestionTypeEnum;

import lombok.Data;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.JsonNode;

/** 题库题目创建/修改请求（作业直接录入的 QuestionInput 继承本类）。 */
@Data
public class QuestionSaveRequest {

    @NonNull
    private QuestionTypeEnum type;

    @NonNull
    private String stem;

    /** 选择题选项（仅选择题必填）。 */
    private List<OptionItem> options;

    /** 标准答案（结构按题型；ESSAY 为参考文本可空）。 */
    private JsonNode answer;

    private String analysis;

    /** 默认配分（默认 5.0）。 */
    private BigDecimal defaultScore;

    /** 多选计分规则（默认 HALF_ON_PARTIAL）。 */
    private MultiScoreRuleEnum scoreRule;

    /** 填空大小写敏感（默认 false）。 */
    private Boolean caseSensitive;

    /** 大题必须传附件（默认 false）。 */
    private Boolean requireFile;

    /** 课程标签（与 campaignId 二选一，可空）。 */
    private Long courseId;

    private Long campaignId;
}
