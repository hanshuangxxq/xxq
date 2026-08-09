package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 毕业设计选题申请状态：PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回。
 */
@Getter
public enum SelectionStatusEnum {

    PENDING("PENDING", "待审核"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    SelectionStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static SelectionStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (SelectionStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知选题申请状态: " + value);
    }
}
