package com.xrq.xxq.module.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("local")
public class Local {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String building;  // 教学楼
    private String classRoom; // 教室
    private Integer max;     // 最大容纳人数
}
