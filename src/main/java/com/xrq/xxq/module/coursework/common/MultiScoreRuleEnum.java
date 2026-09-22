package com.xrq.xxq.module.coursework.common;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/** 多选题计分规则（教师选题时选择）。 */
@Getter
public enum MultiScoreRuleEnum {

    ALL_OR_NOTHING("ALL_OR_NOTHING", "全对才得分"),
    HALF_ON_PARTIAL("HALF_ON_PARTIAL", "少选得半分");

    /** 默认规则：少选半分。 */
    public static final MultiScoreRuleEnum DEFAULT = HALF_ON_PARTIAL;

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    MultiScoreRuleEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /** 请求入参兼容 code/name/中文描述；未命中返回 null。 */
    @JsonCreator
    public static MultiScoreRuleEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (MultiScoreRuleEnum e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.description.equals(value)) {
                return e;
            }
        }
        return null;
    }

    /** null → 默认规则。 */
    public static MultiScoreRuleEnum orDefault(MultiScoreRuleEnum rule) {
        return rule == null ? DEFAULT : rule;
    }
}
