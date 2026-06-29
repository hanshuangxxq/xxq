package com.xrq.xxq.module.user.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @类名 Admin
 * @Date 2026/6/29
 * 系统管理员
 */
@Data
@TableName("admin")
public class Admin {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String password;
}
