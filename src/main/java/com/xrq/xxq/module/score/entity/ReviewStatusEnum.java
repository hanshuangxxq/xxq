package com.xrq.xxq.module.score.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 成绩复核状态枚举。
 * <p>
 * 流转：PENDING -> TEACHER_REPLIED -> ESCALATED -> RESOLVED/REJECTED。
 */
@Getter
public enum ReviewStatusEnum {

    PENDING("PENDING", "待教师处理"),
    TEACHER_REPLIED("TEACHER_REPLIED", "教师已回复"),
    ESCALATED("ESCALATED", "已升级教务"),
    RESOLVED("RESOLVED", "已解决"),
    REJECTED("REJECTED", "已驳回");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    ReviewStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static ReviewStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ReviewStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知复核状态: " + value);
    }
}
