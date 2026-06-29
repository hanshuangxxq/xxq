package com.xrq.xxq.module.user.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @类名 AcademicAdmin
 * @Date 2026/6/29
 * 教务管理员
 */
@Data
@TableName("academic_admin")
public class AcademicAdmin {
    @TableId (type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String departmentNo;
}
