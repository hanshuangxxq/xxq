package com.xrq.xxq.module.practice.graduation.dto;

import lombok.Data;

/**
 * 教务最终审查请求（教务管理者对匹配记录做最后审查）。
 */
@Data
public class AssignmentReviewRequest {

    private Boolean approved;       // true 审查通过 / false 驳回（学生回匹配池）
    private String comment;         // 审查意见
}
