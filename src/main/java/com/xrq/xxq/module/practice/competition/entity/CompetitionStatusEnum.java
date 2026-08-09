package com.xrq.xxq.module.practice.competition.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 竞赛状态：DRAFT 草稿 / OPEN 开放报名 / CLOSED 报名关闭 / ENDED 已结束。
 */
@Getter
public enum CompetitionStatusEnum {

    DRAFT("DRAFT", "草稿"),
    OPEN("OPEN", "开放报名"),
    CLOSED("CLOSED", "报名关闭"),
    ENDED("ENDED", "已结束");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    CompetitionStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static CompetitionStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (CompetitionStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知竞赛状态: " + value);
    }
}
