package com.xrq.xxq.module.selection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 选课班实体（分班结果）。
 */
@Data
@TableName("selection_class")
public class SelectionClass {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campaignId;
    private Long courseId;
    private Integer classNo;
    private Integer studentCount;
}
