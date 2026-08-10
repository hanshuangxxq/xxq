package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 选题活动实体（教务开启）。
 * <p>
 * supervisor_capacity / free_select_capacity 为「每教师」配额，全校统一（同一活动所有教师相同）。
 * free_select_capacity ≤ supervisor_capacity；差额部分由院系管理者统一分配补足。
 * allowed_grade_ids 为允许参与的学生年级 id 列表（逗号分隔，空表示不限）。
 */
@Data
@TableName("graduation_campaign")
public class GraduationCampaign {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long semesterId;
    private String title;
    private LocalDateTime deadline;          // 申报截止时间
    private Integer supervisorCapacity;      // 每教师最大指导数（统一）
    private Integer freeSelectCapacity;      // 每教师自选数（≤ supervisorCapacity，统一）
    private String allowedGradeIds;          // 允许年级 id（逗号分隔，空=不限）
    private CampaignStatusEnum status;       // DRAFT/OPEN/CLOSED
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
