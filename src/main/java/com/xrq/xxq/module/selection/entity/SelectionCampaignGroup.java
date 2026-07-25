package com.xrq.xxq.module.selection.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 选课活动-选课组关联实体。
 * <p>
 * 绑定关系：一个选课组可被多个选课活动绑定，但一个选课活动只能绑定一个选课组。
 * 该“活动->组”的一对一约束在服务层（{@code SelectionGroupServiceImpl.bindToCampaign}）强制，
 * 不在数据库层加唯一约束。sortOrder 字段保留在关联表上以兼容历史数据，在一对一约束下不再具备业务含义。
 */
@Data
@TableName("selection_campaign_group")
public class SelectionCampaignGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campaignId;
    private Long groupId;
    private Integer sortOrder;
    private LocalDateTime createTime;
}
