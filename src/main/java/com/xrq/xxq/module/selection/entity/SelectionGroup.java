package com.xrq.xxq.module.selection.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 选课组实体：独立存在，可被多个选课活动通过 selection_campaign_group 关联表绑定。
 * 按组限制每位学生可选课程数。
 */
@Data
@TableName("selection_group")
public class SelectionGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer maxCourses;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
