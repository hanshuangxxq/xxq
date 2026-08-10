package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 选题申报状态：
 * <ul>
 *   <li>PENDING_DEPT 待院系初审</li>
 *   <li>DEPT_APPROVED 院系初审通过（进入匹配池）</li>
 *   <li>DEPT_REJECTED 院系初审驳回</li>
 *   <li>ASSIGNED 已匹配教师</li>
 * </ul>
 */
@Getter
public enum ProposalStatusEnum {

    PENDING_DEPT("PENDING_DEPT", "待院系初审"),
    DEPT_APPROVED("DEPT_APPROVED", "院系初审通过"),
    DEPT_REJECTED("DEPT_REJECTED", "院系初审驳回"),
    ASSIGNED("ASSIGNED", "已匹配教师");

    @EnumValue
    private final String code;

    @JsonValue
    private final String description;

    ProposalStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static ProposalStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ProposalStatusEnum e : values()) {
            if (e.code.equals(value) || e.description.equals(value) || e.name().equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知选题申报状态: " + value);
    }
}
