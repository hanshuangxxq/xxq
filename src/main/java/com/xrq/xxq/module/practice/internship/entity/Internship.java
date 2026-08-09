package com.xrq.xxq.module.practice.internship.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 实习项目实体。
 * <p>
 * supervisor_id 存负责教师 user.id；selected_count 仅记录审核通过人数
 * （待审核报名不占容量，由 Redis InternshipPendingStore 跟踪）。
 */
@Data
@TableName("internship")
public class Internship {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long semesterId;
    private String title;
    private String company;             // 实习单位
    private String description;
    private Long supervisorId;          // 负责教师 user.id
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
    private Integer selectedCount;
    private InternshipStatusEnum status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
