package com.xrq.xxq.module.exam.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 考试类型枚举：期末/期中/补考/重修。
 */
@Getter
public enum ExamTypeEnum {

    FINAL("FINAL", "期末考试"),
    MIDTERM("MIDTERM", "期中考试"),
    MAKEUP("MAKEUP", "补考"),
    RETAKE("RETAKE", "重修");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    ExamTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static ExamTypeEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ExamTypeEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知考试类型: " + value);
    }
}
