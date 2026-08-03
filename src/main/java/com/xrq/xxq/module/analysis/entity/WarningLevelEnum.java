package com.xrq.xxq.module.analysis.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 预警级别：黄 &lt; 橙 &lt; 红。学生命中多级时取最高级。
 * <p>持久化用 {@code code}（YELLOW）；响应输出用 {@code description}（黄色预警）；
 * 入参既可传 code 也可传 description。
 */
@Getter
public enum WarningLevelEnum {
    YELLOW("YELLOW", "黄色预警"),
    ORANGE("ORANGE", "橙色预警"),
    RED("RED", "红色预警");

    @EnumValue
    private final String code;
    @JsonValue
    private final String description;

    WarningLevelEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static WarningLevelEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (WarningLevelEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知预警级别: " + value);
    }
}
