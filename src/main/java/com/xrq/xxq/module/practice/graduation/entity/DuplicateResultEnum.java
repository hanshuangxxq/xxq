package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 查重结论：PASS 通过 / FAIL 不通过。
 */
@Getter
public enum DuplicateResultEnum {

    PASS("PASS", "通过"),
    FAIL("FAIL", "不通过");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    DuplicateResultEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static DuplicateResultEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (DuplicateResultEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知查重结论: " + value);
    }
}
