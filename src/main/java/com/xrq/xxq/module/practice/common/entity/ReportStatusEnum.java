package com.xrq.xxq.module.practice.common.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 实践模块通用报告状态：SUBMITTED 已提交 / REVIEWED 已评审。
 * <p>
 * 供实习报告、社会实践报告复用。
 */
@Getter
public enum ReportStatusEnum {

    SUBMITTED("SUBMITTED", "已提交"),
    REVIEWED("REVIEWED", "已评审");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    ReportStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static ReportStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ReportStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知报告状态: " + value);
    }
}
