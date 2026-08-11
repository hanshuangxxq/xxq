package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 毕设活动状态：DRAFT 草稿 / OPEN 进行中 / CLOSED 已结束。
 * <p>
 * 创建后为 DRAFT（仅教务可见）；开放后学生可提交选题、教师可自选学生；
 * 教务结束活动后为 CLOSED，写操作全部禁用，仅保留查看。
 */
@Getter
public enum CampaignStatusEnum {

    DRAFT("DRAFT", "草稿"),
    OPEN("OPEN", "进行中"),
    CLOSED("CLOSED", "已结束");

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
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知活动状态: " + value);
    }
}
