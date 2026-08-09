package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 毕业设计选题状态：DRAFT 草稿 / OPEN 开放选题 / CLOSED 关闭。
 */
@Getter
public enum TopicStatusEnum {

    DRAFT("DRAFT", "草稿"),
    OPEN("OPEN", "开放"),
    CLOSED("CLOSED", "关闭");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    TopicStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static TopicStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (TopicStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知选题状态: " + value);
    }
}
