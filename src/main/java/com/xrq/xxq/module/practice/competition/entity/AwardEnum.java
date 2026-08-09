package com.xrq.xxq.module.practice.competition.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 竞赛获奖等级：FIRST 一等奖 / SECOND 二等奖 / THIRD 三等奖 / EXCELLENCE 优秀奖 / PARTICIPATION 参与奖。
 */
@Getter
public enum AwardEnum {

    FIRST("FIRST", "一等奖"),
    SECOND("SECOND", "二等奖"),
    THIRD("THIRD", "三等奖"),
    EXCELLENCE("EXCELLENCE", "优秀奖"),
    PARTICIPATION("PARTICIPATION", "参与奖");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    AwardEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static AwardEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (AwardEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知获奖等级: " + value);
    }
}
