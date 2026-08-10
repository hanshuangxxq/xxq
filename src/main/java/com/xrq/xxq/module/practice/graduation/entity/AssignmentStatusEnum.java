package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 匹配记录状态：MATCHED 已匹配（待教务审查）/ APPROVED 教务审查通过 / REJECTED 教务审查驳回。
 */
@Getter
public enum AssignmentStatusEnum {

    MATCHED("MATCHED", "已匹配"),
    APPROVED("APPROVED", "审查通过"),
    REJECTED("REJECTED", "审查驳回");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    AssignmentStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static AssignmentStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (AssignmentStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value) || e.name().equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知匹配状态: " + value);
    }
}
