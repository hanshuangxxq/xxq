package com.xrq.xxq.module.selection.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 选课活动实体。
 */
@Data
@TableName("selection_campaign")
public class SelectionCampaign {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long semesterId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxCoursesPerStudent;
    private CampaignStatusEnum status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
