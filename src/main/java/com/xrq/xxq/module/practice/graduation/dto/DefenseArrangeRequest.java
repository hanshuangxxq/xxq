package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 院系安排答辩（R-9.1，仅查重通过学生可被安排）。
 */
@Data
public class DefenseArrangeRequest {

    /** 活动ID */
    @NonNull
    private Long campaignId;

    /** 学生 user.id */
    @NonNull
    private Long studentId;

    /** 答辩分组名称 */
    private String groupName;

    private LocalDateTime defenseTime;

    /** 答辩地点 */
    private String location;

    /** 评阅教师 user.id */
    private Long reviewerId;

    /** 答辩教师 user.id 列表 */
    private List<Long> defenseTeacherIds;
}
