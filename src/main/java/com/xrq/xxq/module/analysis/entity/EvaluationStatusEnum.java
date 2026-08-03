package com.xrq.xxq.module.analysis.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 评教周期状态：已开放 / 已关闭（含未设置）。
 */
@Getter
public enum EvaluationStatusEnum {
    OPEN("OPEN", "已开放"),
    CLOSED("CLOSED", "已关闭");

    @EnumValue
    private final String code;
    @JsonValue
    private final String description;

    EvaluationStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static EvaluationStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (EvaluationStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知评教状态: " + value);
    }
}
