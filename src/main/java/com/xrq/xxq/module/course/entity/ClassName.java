package com.xrq.xxq.module.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @类名 ClassName
 * @Date 2026/6/30
 *
 */
@Data
@TableName("class_name")
public class ClassName {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String className; // 班级名称
    private String college; // 学院
}
