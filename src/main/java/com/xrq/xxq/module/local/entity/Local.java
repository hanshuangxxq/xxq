package com.xrq.xxq.module.local.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 地点类
 * 主要是体现教室信息
 */
@Data
@TableName("local")
public class Local {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String building;  // 教学楼
    private String classRoom; // 教室
    private Integer max;      // 最大容纳人数
    private LocalTypeEnum type; // 教室类型: 1普通教室 2实验室 3机房 4报告厅
    private Long managerId;   // 管理者教师ID（FK -> teacher.id），实验室/机房必填
    @TableField(exist = false)
    private String managerName; // 管理者姓名（回显用，非数据库字段）
}
