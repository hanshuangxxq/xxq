package com.xrq.xxq.module.analysis.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 模板-指标关联（含快照）：item_name/max_score 为绑定或同步时的快照。
 * 编辑指标库时通过 updateTemplates 开关决定是否刷新本表快照。
 */
@Data
@TableName("evaluation_template_item")
public class EvaluationTemplateItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private Long itemId;
    private String itemName;        // 指标名称快照
    private Integer maxScore;       // 满分快照
    private Integer sortOrder;      // 排序，从小到大
    private Integer required;       // 0:选填 1:必填
    private LocalDateTime createTime;
}
