package com.xrq.xxq.module.analysis.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 评教模板状态：ENABLED 启用 / DISABLED 停用。停用的模板不可被评教使用。
 */
@Getter
public enum EvaluationTemplateStatusEnum {

    ENABLED("ENABLED", "启用"),
    DISABLED("DISABLED", "停用");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    EvaluationTemplateStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static EvaluationTemplateStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (EvaluationTemplateStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知模板状态: " + value);
    }
}
