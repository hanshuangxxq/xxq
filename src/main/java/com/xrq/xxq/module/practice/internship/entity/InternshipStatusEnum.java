package com.xrq.xxq.module.practice.internship.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 实习项目状态：DRAFT 草稿 / OPEN 开放报名 / CLOSED 关闭。
 */
@Getter
public enum InternshipStatusEnum {

    DRAFT("DRAFT", "草稿"),
    OPEN("OPEN", "开放"),
    CLOSED("CLOSED", "关闭");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    InternshipStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static InternshipStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (InternshipStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知实习状态: " + value);
    }
}
