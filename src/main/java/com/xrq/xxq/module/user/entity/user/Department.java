package com.xrq.xxq.module.user.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @类名 Department
 * @Date 2026/6/29
 * 院系管理员
 */
@Data
@TableName("department")
public class Department {
    @TableId (type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long collegeId;     // 所属院系 -> college.id（该管理员管理的院系）
}
