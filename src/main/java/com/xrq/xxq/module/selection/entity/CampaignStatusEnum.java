package com.xrq.xxq.module.selection.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 选课活动状态：DRAFT 草稿 / OPEN 开放选课 / CLOSED 关闭 / FINALIZED 已分班。
 */
@Getter
public enum CampaignStatusEnum {
    DRAFT("DRAFT"),
    OPEN("OPEN"),
    CLOSED("CLOSED"),
    FINALIZED("FINALIZED");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    CampaignStatusEnum(String code) {
        this.code = code;
        this.description = code;
    }
}
