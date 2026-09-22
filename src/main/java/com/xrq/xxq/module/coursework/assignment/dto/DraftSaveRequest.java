package com.xrq.xxq.module.coursework.assignment.dto;

import java.util.List;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 草稿保存请求（整体覆盖语义：传什么存什么，空列表=清空草稿）。仅写 Redis。 */
@Data
public class DraftSaveRequest {

    @NonNull
    private List<AnswerInput> answers;
}
