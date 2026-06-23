package com.xrq.xxq.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 微信登录关联实体，存储微信 openid/unionid 与系统用户的绑定关系。
 * 通过 {@code type} 字段指明关联的用户表，{@code userId} 定位具体记录。
 *
 * @类名 WXUser
 * @Date 2026/6/22
 */
@Data
@TableName("wx_user")
public class WXUser {
    @TableId(type = IdType.AUTO)
    private Long id;          // 主键id
    private String type;     // 对应的表
    private Long userId;      // 关联的用户表主键id
    private String userType;  // 用户类型（teacher/student/dean）
    private String wxOpenid;  // 微信openid
    private String wxUnionid; // 微信unionid
}
