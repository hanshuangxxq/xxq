package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 中期检查结论：NORMAL 正常 / WARNING 警告 / SEVERE_LAGGING 严重滞后。
 * <p>
 * 由指导教师给出，结论进入教务看板供预警（R-7.5）。
 */
@Getter
public enum MidtermConclusionEnum {

    NORMAL("NORMAL", "正常"),
    WARNING("WARNING", "警告"),
    SEVERE_LAGGING("SEVERE_LAGGING", "严重滞后");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    MidtermConclusionEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static MidtermConclusionEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (MidtermConclusionEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知中期结论: " + value);
    }
}
