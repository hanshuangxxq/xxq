package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 论文状态机（阶段三）：
 * <pre>
 * 学生提交 → SUBMITTED（待形式审查）
 *   ├─ 教师形式审查通过 → APPROVED（待查重）
 *   └─ 教师退回 → REVISION（可重提新版本，回到 SUBMITTED）
 * APPROVED → 教务登记查重结果
 *   ├─ 查重通过 → DUPLICATE_PASSED（门禁：可进入答辩）
 *   └─ 查重不通过 → DUPLICATE_FAILED（可重提新版本，回到 SUBMITTED）
 * </pre>
 */
@Getter
public enum ThesisStatusEnum {

    SUBMITTED("SUBMITTED", "待形式审查"),
    APPROVED("APPROVED", "形式审查通过"),
    REVISION("REVISION", "形式审查退回"),
    DUPLICATE_PASSED("DUPLICATE_PASSED", "查重通过"),
    DUPLICATE_FAILED("DUPLICATE_FAILED", "查重不通过");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    ThesisStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static ThesisStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ThesisStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知论文状态: " + value);
    }
}
