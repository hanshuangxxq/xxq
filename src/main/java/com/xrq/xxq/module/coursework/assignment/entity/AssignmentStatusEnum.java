package com.xrq.xxq.module.coursework.assignment.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/** 作业状态：DRAFT 草稿 / PUBLISHED 已发布 / CLOSED 已关闭（停止提交）。 */
@Getter
public enum AssignmentStatusEnum {

    DRAFT("DRAFT", "草稿"),
    PUBLISHED("PUBLISHED", "已发布"),
    CLOSED("CLOSED", "已关闭");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    AssignmentStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
