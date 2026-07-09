package com.xrq.xxq.module.selection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 选课活动可选课程实体。
 */
@Data
@TableName("selection_course")
public class SelectionCourse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campaignId;
    private Long courseId;
    private Integer capacity;
}
