package com.xrq.xxq.module.exam.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 补考/重修候选人（不及格学生）。
 */
@Data
public class MakeupCandidateDto {

    private Long studentUserId;
    private String studentName;
    private String studentNo;
    private Long scoreId;          // 原不及格成绩 id
    private BigDecimal totalScore; // 原总评
    private String scoreLevel;
    private Long semesterId;       // 原成绩学期
}
