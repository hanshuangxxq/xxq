package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 论文状态：SUBMITTED 已提交 / UNDER_REVIEW 评审中 / PASSED 通过 / FAILED 未通过 / REVISION 需修改。
 */
@Getter
public enum ThesisStatusEnum {

    SUBMITTED("SUBMITTED", "已提交"),
    UNDER_REVIEW("UNDER_REVIEW", "评审中"),
    PASSED("PASSED", "通过"),
    FAILED("FAILED", "未通过"),
    REVISION("REVISION", "需修改");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    ThesisStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static ThesisStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ThesisStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知论文状态: " + value);
    }
}
