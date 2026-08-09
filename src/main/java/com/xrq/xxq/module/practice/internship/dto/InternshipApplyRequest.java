package com.xrq.xxq.module.practice.internship.dto;

import lombok.Data;

/**
 * 学生实习报名请求。
 */
@Data
public class InternshipApplyRequest {

    private Long internshipId;
    private String applyReason;
}
