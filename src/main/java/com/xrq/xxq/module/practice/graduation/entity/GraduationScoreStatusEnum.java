package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 毕设成绩状态：INCOMPLETE 分项未齐备 / COMPLETE 已合成总评 / PUBLISHED 已发布。
 * <p>
 * 三项分项评分（指导/评阅/答辩）录入齐全后自动合成总评 → COMPLETE；
 * 院系管理者确认后发布给学生 → PUBLISHED（终态，不可再改）。
 */
@Getter
public enum GraduationScoreStatusEnum {

    INCOMPLETE("INCOMPLETE", "分项未齐备"),
    COMPLETE("COMPLETE", "已合成总评"),
    PUBLISHED("PUBLISHED", "已发布");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    GraduationScoreStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static GraduationScoreStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (GraduationScoreStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知成绩状态: " + value);
    }
}
