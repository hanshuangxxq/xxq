package com.xrq.xxq.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.NonNull;

import java.time.LocalDateTime;

/**
 * @类名 User
 * @Date 2026/6/5
 * 用户基类（抽象），定义所有用户类型的公共字段
 */
@Data
@EqualsAndHashCode
public abstract class User {
    @TableId (type = IdType.AUTO)
    private Long id; //主键id
    @NonNull
    private String name; //用户名
    private String password; //密码
    private String email; //邮箱
    private String phone; //手机号
    private GenderEnum gender; //性别
    private String avatar; //头像（URL）
    private String description; //简介
    private String role; //权限
    private LocalDateTime lastLoginTime; //上次登录时间
    private LocalDateTime createTime; //账号创建时间
    private Integer status; //账号状态 0:正常 1:禁用
    @TableLogic (value = "0", delval = "1")
    private Boolean deleted; //逻辑删除

}
