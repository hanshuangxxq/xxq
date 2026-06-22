package com.xrq.xxq.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @类名 QQUser
 * @Date 2026/6/22
 * QQ登录
 */
@Data
@TableName("qq_user")
public class QQUser {
    @TableId(type = IdType.AUTO)
    private Long id; //主键id
    private String userId; //关联的用户id
    private String qqOpenid; //QQ openid
}
