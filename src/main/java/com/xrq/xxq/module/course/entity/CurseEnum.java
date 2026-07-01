package com.xrq.xxq.module.course.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * @类名 CurseEnum
 * @Date 2026/6/30
 * 主要是描述课程的类型，例如：必修、选修、公选、实践等
 */
@Getter
public enum CurseEnum {
    REQUIRE(1,"必修"),
    ELECTIVE(2,"选修"),
    PUBLIC(3,"公选"),
    PRACTICE(4,"实践");

    @EnumValue
    private final Integer value;
    private final String description;

    CurseEnum (Integer value, String description) {
        this.value = value;
        this.description = description;
    }
}
