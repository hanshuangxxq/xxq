package com.xrq.xxq.module.selection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 选课活动与时段限制的多对多关联实体。
 * <p>
 * 每个 selection_campaign 可关联多个 TimeRestriction（RESERVED 类型），
 * 排课求解器从这些预留时段中挑选一个不冲突的分配给该课程的教学班。
 */
@Data
@TableName("selection_campaign_time_restriction")
public class SelectionCampaignTimeRestriction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campaignId;
    private Long timeRestrictionId;
}
