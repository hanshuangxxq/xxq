package com.xrq.xxq.module.practice.common.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 实践模块通用审核状态：PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回。
 * <p>
 * 供实习报名、竞赛报名、社会实践申报等场景复用。
 */
@Getter
public enum AuditStatusEnum {

    PENDING("PENDING", "待审核"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    AuditStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static AuditStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (AuditStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知审核状态: " + value);
    }
}
