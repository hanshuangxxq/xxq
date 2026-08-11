package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 师生匹配来源：TEACHER_PICK 教师自选 / DEPT_ALLOCATE 院系指定。
 */
@Getter
public enum AssignmentSourceEnum {

    TEACHER_PICK("TEACHER_PICK", "教师自选"),
    DEPT_ALLOCATE("DEPT_ALLOCATE", "院系指定");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    AssignmentSourceEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static AssignmentSourceEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (AssignmentSourceEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知匹配来源: " + value);
    }
}
