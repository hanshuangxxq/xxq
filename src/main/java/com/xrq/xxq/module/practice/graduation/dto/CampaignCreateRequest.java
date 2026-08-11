package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 创建毕设活动（§4.1）。
 */
@Data
public class CampaignCreateRequest {

    /** 活动名称（同年度内唯一） */
    @NonNull
    private String name;

    /** 参与年级ID列表（可多选） */
    @NonNull
    private List<Long> allowedGradeIds;

    /** 选题开始时间 */
    @NonNull
    private LocalDateTime topicStartTime;

    /** 选题截止时间（必须晚于开始时间） */
    @NonNull
    private LocalDateTime topicEndTime;

    /** 教师可分配学生数上限（正整数） */
    @NonNull
    private Integer supervisorCapacity;

    /** 教师自由选择学生数上限（正整数，<= supervisorCapacity） */
    @NonNull
    private Integer freeSelectCapacity;

    /** 开题报告提交窗口（阶段二，可空） */
    private LocalDateTime openingStartTime;

    private LocalDateTime openingEndTime;

    /** 中期检查提交窗口（阶段二，可空） */
    private LocalDateTime midtermStartTime;

    private LocalDateTime midtermEndTime;

    /** 论文提交窗口（阶段三，可空） */
    private LocalDateTime thesisStartTime;

    private LocalDateTime thesisEndTime;

    /** 指导教师评分权重（阶段四，默认30） */
    private Integer advisorWeight;

    /** 评阅教师评分权重（阶段四，默认20） */
    private Integer reviewerWeight;

    /** 答辩评分权重（阶段四，默认50） */
    private Integer defenseWeight;
}
