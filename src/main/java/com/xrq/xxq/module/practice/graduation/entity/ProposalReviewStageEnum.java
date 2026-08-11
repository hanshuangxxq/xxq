package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 选题审批级别：DEPT 院系初审 / ACADEMIC 教务终审。
 * <p>
 * 用于审批流水表记录两级审批的留痕（R-5.7）。
 */
@Getter
public enum ProposalReviewStageEnum {

    DEPT("DEPT", "院系初审"),
    ACADEMIC("ACADEMIC", "教务终审");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    ProposalReviewStageEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static ProposalReviewStageEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ProposalReviewStageEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知审批级别: " + value);
    }
}
