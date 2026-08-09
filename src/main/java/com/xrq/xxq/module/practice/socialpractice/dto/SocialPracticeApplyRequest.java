package com.xrq.xxq.module.practice.socialpractice.dto;

import lombok.Data;

/**
 * 社会实践申报请求（个人/团队）。
 */
@Data
public class SocialPracticeApplyRequest {

    private Long practiceId;
    private String teamName;            // 团队名（个人为空）
    private String members;             // 团队成员 user.id 逗号分隔（个人为空）
    private String applyReason;
}
