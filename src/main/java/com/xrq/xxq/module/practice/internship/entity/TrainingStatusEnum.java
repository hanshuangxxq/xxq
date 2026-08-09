package com.xrq.xxq.module.practice.internship.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 培训课程状态：DRAFT 草稿 / OPEN 开放报名 / CLOSED 关闭。
 */
@Getter
public enum TrainingStatusEnum {

    DRAFT("DRAFT", "草稿"),
    OPEN("OPEN", "开放"),
    CLOSED("CLOSED", "关闭");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    TrainingStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static TrainingStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (TrainingStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知培训状态: " + value);
    }
}
