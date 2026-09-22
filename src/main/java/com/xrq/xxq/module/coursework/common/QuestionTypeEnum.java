package com.xrq.xxq.module.coursework.common;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/** 题型。JSON 入参经 {@link #fromValue} 兼容 code/name/中文描述，避免 ordinal 陷阱。 */
@Getter
public enum QuestionTypeEnum {

    SINGLE_CHOICE("SINGLE_CHOICE", "单选题"),
    MULTI_CHOICE("MULTI_CHOICE", "多选题"),
    JUDGE("JUDGE", "判断题"),
    FILL_BLANK("FILL_BLANK", "填空题"),
    ESSAY("ESSAY", "简答题");

    /** 持久化与 JSON 输出都用 code：前端按 code 分发题型组件。 */
    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    QuestionTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /** 请求入参兼容 code/name/中文描述；未命中返回 null，由业务层给出可读 400。 */
    @JsonCreator
    public static QuestionTypeEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (QuestionTypeEnum e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.description.equals(value)) {
                return e;
            }
        }
        return null;
    }
}
