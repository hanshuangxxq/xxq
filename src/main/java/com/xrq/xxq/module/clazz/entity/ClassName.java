package com.xrq.xxq.module.clazz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 班级名称类
 */
@Data
@TableName("class_name")
public class ClassName {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String className; // 班级名称
    private Long collegeId; // 所属院系 -> college.id
}
