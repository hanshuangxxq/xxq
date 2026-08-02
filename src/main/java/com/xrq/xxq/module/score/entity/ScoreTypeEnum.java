package com.xrq.xxq.module.score.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 成绩类型枚举。
 * <p>
 * 持久化用 {@code code}（如 REGULAR）；响应输出用 {@code description}（如 正常）；
 * 请求入参既可传 code 也可传 description，由 {@link #fromValue} 统一解析。
 */
@Getter
public enum ScoreTypeEnum {

    REGULAR("REGULAR", "正常"),
    MAKEUP("MAKEUP", "补考"),
    RETAKE("RETAKE", "重修");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    ScoreTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static ScoreTypeEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ScoreTypeEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知成绩类型: " + value);
    }
}
