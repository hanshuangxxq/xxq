package com.xrq.xxq.module.coursework.assignment.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 作业标准答案/解析的可见性（学生端、且仅针对已提交的查看场景）。
 * 作答页（未提交）任何档位都不给标准答案。
 */
@Getter
public enum AnswerVisibleEnum {

    /** 提交后即可看（默认，对齐学习通）。 */
    SUBMIT("SUBMIT", "提交后可见"),
    /** 截止时间后可见。 */
    DEADLINE("DEADLINE", "截止后可见"),
    /** 作业关闭后可见。 */
    CLOSED("CLOSED", "关闭后可见"),
    /** 永不可见。 */
    NEVER("NEVER", "不可见");

    public static final AnswerVisibleEnum DEFAULT = SUBMIT;

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    AnswerVisibleEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static AnswerVisibleEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (AnswerVisibleEnum e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.description.equals(value)) {
                return e;
            }
        }
        return null;
    }

    public static AnswerVisibleEnum orDefault(AnswerVisibleEnum v) {
        return v == null ? DEFAULT : v;
    }

    /** 当前时刻是否可见（调用方保证学生已提交；SUBMIT 档恒 true）。 */
    public boolean visible(LocalDateTime deadline, AssignmentStatusEnum status, LocalDateTime now) {
        return switch (this) {
            case SUBMIT -> true;
            case DEADLINE -> deadline != null && now.isAfter(deadline);
            case CLOSED -> status == AssignmentStatusEnum.CLOSED;
            case NEVER -> false;
        };
    }
}
