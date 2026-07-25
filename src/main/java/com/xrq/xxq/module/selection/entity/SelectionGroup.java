package com.xrq.xxq.module.selection.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 选课组实体：按组限制每位学生可选课程数。
 */
@Data
@TableName("selection_group")
public class SelectionGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campaignId;
    private String name;
    private Integer maxCourses;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
