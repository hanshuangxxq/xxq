package com.xrq.xxq.module.local.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 教室类型枚举，例如：普通教室、实验室、机房、报告厅等。
 * <p>
 * 持久化用 {@code value}（1普通教室 2实验室 3机房 4报告厅）；响应输出用 {@code description}；
 * 请求入参（@RequestBody）既可传 name（CLASSROOM）也可传 value（1）或 description（普通教室），
 * 由 {@link #fromValue} 统一解析。
 */
@Getter
public enum LocalTypeEnum {
    CLASSROOM(1, "普通教室"),
    LABORATORY(2, "实验室"),
    COMPUTER_ROOM(3, "机房"),
    LECTURE_HALL(4, "报告厅");

    @EnumValue
    private final Integer value;
    @JsonValue
    private final String description;

    LocalTypeEnum(Integer value, String description) {
        this.value = value;
        this.description = description;
    }

    @JsonCreator
    public static LocalTypeEnum fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (LocalTypeEnum e : values()) {
            if (e.name().equals(value) || e.description.equals(value) || String.valueOf(e.value).equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知教室类型: " + value);
    }
}
