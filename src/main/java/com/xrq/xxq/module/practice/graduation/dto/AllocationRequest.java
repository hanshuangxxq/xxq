package com.xrq.xxq.module.practice.graduation.dto;

import lombok.Data;

/**
 * 院系统一分配请求（院系管理者将本学院池中学生分配给本学院教师）。
 */
@Data
public class AllocationRequest {

    private Long campaignId;       // 活动 id
    private Long proposalId;       // 选题申报 id（即被分配学生）
    private Long teacherId;        // 指派教师 user.id（本学院）
}
