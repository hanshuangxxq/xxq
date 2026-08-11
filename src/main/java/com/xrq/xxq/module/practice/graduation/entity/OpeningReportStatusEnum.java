package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 开题报告状态：SUBMITTED 已提交 / APPROVED 已通过 / REVISION 需修改。
 * <p>
 * 指导教师审核：通过 → APPROVED（终态）；退回 → REVISION（学生可修改重提，回到 SUBMITTED）。
 */
@Getter
public enum OpeningReportStatusEnum {

    SUBMITTED("SUBMITTED", "已提交"),
    APPROVED("APPROVED", "已通过"),
    REVISION("REVISION", "需修改");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    OpeningReportStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static OpeningReportStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (OpeningReportStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知开题报告状态: " + value);
    }
}
