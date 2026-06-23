package com.xrq.xxq.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 支付宝登录关联实体，存储支付宝 user_id 与系统用户的绑定关系。
 * 通过 {@code type} 字段指明关联的用户表，{@code userId} 定位具体记录。
 *
 * @类名 AlipayUser
 * @Date 2026/6/22
 */
@Data
@TableName("alipay_user")
public class AlipayUser {
    @TableId(type = IdType.AUTO)
    private Long id;             // 主键id
    private String type;        // 对应的表
    private Long userId;         // 关联的用户表主键id
    private String userType;     // 用户类型（teacher/student/dean）
    private String alipayUserId; // 支付宝user_id
}
