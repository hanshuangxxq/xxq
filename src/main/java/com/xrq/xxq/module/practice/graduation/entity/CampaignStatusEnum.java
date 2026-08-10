package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 选题活动状态：DRAFT 草稿 / OPEN 开放申报 / CLOSED 关闭。
 */
@Getter
public enum CampaignStatusEnum {

    DRAFT("DRAFT", "草稿"),
    OPEN("OPEN", "开放"),
    CLOSED("CLOSED", "关闭");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    CampaignStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static CampaignStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (CampaignStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value) || e.name().equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知选题活动状态: " + value);
    }
}
