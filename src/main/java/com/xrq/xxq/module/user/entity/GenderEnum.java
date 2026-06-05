package com.xrq.xxq.module.user.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * @类名 GenderEnum
 * @Date 2026/6/5
 *
 */
@Getter
public enum GenderEnum {
    MALE(0, "未知"),
    FEMALE(1, "男"),
    UNKNOWN(2, "女");
    @EnumValue
    private final int code;
    private final String desc;

    GenderEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
