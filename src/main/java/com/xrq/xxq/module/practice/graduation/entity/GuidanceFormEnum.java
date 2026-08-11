package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 过程指导形式：ONLINE 线上 / OFFLINE 线下 / PHONE 电话。
 */
@Getter
public enum GuidanceFormEnum {

    ONLINE("ONLINE", "线上"),
    OFFLINE("OFFLINE", "线下"),
    PHONE("PHONE", "电话");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    GuidanceFormEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static GuidanceFormEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (GuidanceFormEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知指导形式: " + value);
    }
}
