package com.xrq.xxq.module.coursework.assignment.dto;

import java.util.List;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 提交作业请求：纯 JSON 全量答案（附件先经 file 模块上传拿 path）。 */
@Data
public class SubmitAnswersRequest {

    @NonNull
    private List<AnswerInput> answers;
}
