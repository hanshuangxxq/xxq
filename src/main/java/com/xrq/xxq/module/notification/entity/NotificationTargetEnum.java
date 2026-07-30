package com.xrq.xxq.module.notification.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 广播通知目标群体。
 * <p>
 * 持久化用 {@code code}；STUDENT 表示全体学生，ALL 表示全体用户。
 * 查询可见广播时按 user.user_type 与此字段匹配。
 */
@Getter
public enum NotificationTargetEnum {

    STUDENT("STUDENT"),
    ALL("ALL");

    @EnumValue
    private final String code;

    NotificationTargetEnum(String code) {
        this.code = code;
    }
}
