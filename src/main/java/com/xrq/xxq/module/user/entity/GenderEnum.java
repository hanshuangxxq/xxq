package com.xrq.xxq.module.user.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 性别枚举。
 * 通过 {@link EnumValue} 标记的 {@code code} 字段与数据库交互，MyBatis Plus 自动映射。
 *
 * @类名 GenderEnum
 * @Date 2026/6/5
 */
@Getter
public enum GenderEnum {
    MALE(0, "未知"),
    FEMALE(1, "男"),
    UNKNOWN(2, "女");

    /** 数据库存取值 */
    @EnumValue
    private final int code;
    /** 中文描述 */
    @JsonValue
    private final String desc;

    GenderEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
