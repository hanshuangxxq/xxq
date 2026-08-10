package com.xrq.xxq.module.practice.graduation.dto;

import lombok.Data;

/**
 * 教师自选学生请求（教师从本学院匹配池中选择学生）。
 */
@Data
public class PickRequest {

    private Long campaignId;       // 活动 id
    private Long proposalId;       // 选题申报 id（即被选学生）
}
