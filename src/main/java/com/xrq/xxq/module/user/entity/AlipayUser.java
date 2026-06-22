package com.xrq.xxq.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @类名 AlipayUser
 * @Date 2026/6/22
 * 支付宝登录
 */
@Data
@TableName("alipay_user")
public class AlipayUser {
    @TableId(type = IdType.AUTO)
    private Long id;           // 主键id
    private Long userId;       // 关联的用户表主键id
    private String userType;   // 用户类型（teacher/student/dean）
    private String alipayUserId;// 支付宝user_id
}
