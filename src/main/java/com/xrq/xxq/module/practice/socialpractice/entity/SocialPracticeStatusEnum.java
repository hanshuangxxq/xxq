package com.xrq.xxq.module.practice.socialpractice.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 社会实践项目状态：DRAFT 草稿 / OPEN 开放申报 / CLOSED 关闭。
 */
@Getter
public enum SocialPracticeStatusEnum {

    DRAFT("DRAFT", "草稿"),
    OPEN("OPEN", "开放"),
    CLOSED("CLOSED", "关闭");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    SocialPracticeStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static SocialPracticeStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (SocialPracticeStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知社会实践状态: " + value);
    }
}
