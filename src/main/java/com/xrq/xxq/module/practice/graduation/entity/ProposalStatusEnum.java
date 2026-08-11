package com.xrq.xxq.module.practice.graduation.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 选题申请状态机（阶段一两级审批）：
 * <pre>
 * 学生提交 → PENDING_DEPT（待院系初审）
 *   ├─ 院系驳回 → REJECTED（可修改重提，重提回到 PENDING_DEPT）
 *   └─ 院系通过 → DEPT_APPROVED（待教务终审）
 *        ├─ 教务驳回 → REJECTED（可修改重提）
 *        └─ 教务通过 → APPROVED（审批完毕，终态）
 * </pre>
 */
@Getter
public enum ProposalStatusEnum {

    PENDING_DEPT("PENDING_DEPT", "待院系初审"),
    DEPT_APPROVED("DEPT_APPROVED", "待教务终审"),
    APPROVED("APPROVED", "审批完毕"),
    REJECTED("REJECTED", "已驳回");

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
            if (e.code.equals(value) || e.description.equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知选题状态: " + value);
    }
}
