package com.xrq.xxq.module.practice.competition.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 竞赛级别：NATIONAL 国家级 / PROVINCIAL 省级 / SCHOOL 校级。
 */
@Getter
public enum CompetitionLevelEnum {

    NATIONAL("NATIONAL", "国家级"),
    PROVINCIAL("PROVINCIAL", "省级"),
    SCHOOL("SCHOOL", "校级");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    CompetitionLevelEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static CompetitionLevelEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (CompetitionLevelEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知竞赛级别: " + value);
    }
}
