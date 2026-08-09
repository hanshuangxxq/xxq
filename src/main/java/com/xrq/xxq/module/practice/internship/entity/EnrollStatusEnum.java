package com.xrq.xxq.module.practice.internship.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 培训报名状态：ENROLLED 已报名 / CANCELLED 已取消。
 */
@Getter
public enum EnrollStatusEnum {

    ENROLLED("ENROLLED", "已报名"),
    CANCELLED("CANCELLED", "已取消");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    EnrollStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static EnrollStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (EnrollStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知报名状态: " + value);
    }
}
