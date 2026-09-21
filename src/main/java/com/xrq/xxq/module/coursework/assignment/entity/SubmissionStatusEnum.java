package com.xrq.xxq.module.coursework.assignment.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/** 提交状态：SUBMITTED 已提交待批改 / GRADED 已批改。重交会重置回 SUBMITTED。 */
@Getter
public enum SubmissionStatusEnum {

    SUBMITTED("SUBMITTED", "已提交"),
    GRADED("GRADED", "已批改");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    SubmissionStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
