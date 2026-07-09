package com.xrq.xxq.module.selection.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 选课记录状态：SELECTED 已选 / DROPPED 已退。
 */
@Getter
public enum RecordStatusEnum {
    SELECTED("SELECTED"),
    DROPPED("DROPPED");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    RecordStatusEnum(String code) {
        this.code = code;
        this.description = code;
    }
}
