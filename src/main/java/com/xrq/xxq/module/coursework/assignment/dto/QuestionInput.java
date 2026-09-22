package com.xrq.xxq.module.coursework.assignment.dto;

import java.math.BigDecimal;

import com.xrq.xxq.module.coursework.common.QuestionTypeEnum;
import com.xrq.xxq.module.coursework.question.dto.QuestionSaveRequest;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 作业内直接录入的题目。
 * saveToBank=true（默认）时同时存入教师个人题库，便于其它班级复用。
 *
 * <p>父类 {@link QuestionSaveRequest} 带 (type, stem) 必填构造器（Lombok @Data），
 * 子类若用 @Data 其生成的构造器会隐式调用不存在的 super() 而无法编译——
 * 故子类显式声明同风格构造器（Jackson 单构造器隐式属性绑定完成反序列化）；
 * score 为 null 由服务层 requirePositiveScore 给出可读 400。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class QuestionInput extends QuestionSaveRequest {

    /** 本题在本作业的配分（必填，>0；null → 400「题目配分必须大于 0」）。 */
    private BigDecimal score;

    /** 是否同时存入题库（默认 true）。 */
    private Boolean saveToBank;

    public QuestionInput(QuestionTypeEnum type, String stem, BigDecimal score) {
        super(type, stem);
        this.score = score;
    }
}
