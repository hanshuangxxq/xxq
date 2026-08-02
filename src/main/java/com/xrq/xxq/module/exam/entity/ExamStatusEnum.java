package com.xrq.xxq.module.exam.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 考试状态枚举：已安排/已取消/已完成。
 */
@Getter
public enum ExamStatusEnum {

    SCHEDULED("SCHEDULED", "已安排"),
    CANCELED("CANCELED", "已取消"),
    COMPLETED("COMPLETED", "已完成");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    ExamStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static ExamStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ExamStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知考试状态: " + value);
    }
}
