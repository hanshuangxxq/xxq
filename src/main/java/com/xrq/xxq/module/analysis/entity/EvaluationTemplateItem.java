package com.xrq.xxq.module.analysis.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 模板-指标关联：指标名/满分通过 item_id 关联 evaluation_item 表获取，不在本表冗余。
 */
@Data
@TableName("evaluation_template_item")
public class EvaluationTemplateItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private Long itemId;
    private Integer sortOrder;      // 排序，从小到大
    private Integer required;       // 0:选填 1:必填
    private LocalDateTime createTime;
}
