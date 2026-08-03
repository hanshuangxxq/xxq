package com.xrq.xxq.module.analysis.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 预警记录状态：生效中 / 已解除。
 */
@Getter
public enum WarningStatusEnum {
    ACTIVE("ACTIVE", "生效中"),
    RESOLVED("RESOLVED", "已解除");

    @EnumValue
    private final String code;
    @JsonValue
    private final String description;

    WarningStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static WarningStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (WarningStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知预警状态: " + value);
    }
}
