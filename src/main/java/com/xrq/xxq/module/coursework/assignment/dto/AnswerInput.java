package com.xrq.xxq.module.coursework.assignment.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.JsonNode;

/**
 * 单题作答输入。answer 结构按题型：
 * 单选 "A"；多选 ["A","B"]；判断 true；填空 ["空1","空2"]；
 * 大题 {"text":"...","files":[{"path":"objects/...","original":"报告.pdf"}]}。
 * answer=null 表示未答该题。
 */
@Data
public class AnswerInput {

    @NonNull
    private Long questionId;

    private JsonNode answer;
}
