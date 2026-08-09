package com.xrq.xxq.module.practice.competition.dto;

import lombok.Data;

/**
 * 竞赛报名请求（个人/团队）。
 */
@Data
public class RegistrationRequest {

    private Long competitionId;
    private String teamName;          // 团队名（个人赛为空）
    private String members;           // 团队成员 user.id 逗号分隔（个人赛为空）
}
