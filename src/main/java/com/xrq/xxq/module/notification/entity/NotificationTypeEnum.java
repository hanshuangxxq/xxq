package com.xrq.xxq.module.notification.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 消息类型枚举。后续接入消息来源时按业务场景扩展。
 * <p>
 * 持久化用 {@code code}（如 SYSTEM）；响应输出用 {@code description}（如 系统消息）；
 * 请求入参既可传 code 也可传 description，由 {@link #fromValue} 统一解析。
 */
@Getter
public enum NotificationTypeEnum {

    SYSTEM("SYSTEM", "系统消息"),
    SELECTION("SELECTION", "选课消息"),
    SCHEDULE("SCHEDULE", "排课消息"),
    COURSE("COURSE", "课程消息"),
    GRADE("GRADE", "成绩消息");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    NotificationTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static NotificationTypeEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (NotificationTypeEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知消息类型: " + value);
    }
}
