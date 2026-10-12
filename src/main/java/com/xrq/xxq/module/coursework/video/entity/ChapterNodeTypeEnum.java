package com.xrq.xxq.module.coursework.video.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/** 章节树节点类型：CHAPTER 章 / SECTION 节（固定两级，深度由应用层强制）。 */
@Getter
public enum ChapterNodeTypeEnum {

    CHAPTER("CHAPTER", "章"),
    SECTION("SECTION", "节");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    ChapterNodeTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
