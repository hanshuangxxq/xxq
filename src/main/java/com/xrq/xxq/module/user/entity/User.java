package com.xrq.xxq.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String password;
    private String email;
    private String phone;
    private GenderEnum gender;
    private String avatar;
    private String description;
    private String role;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private Integer status;
    @TableLogic(value = "0", delval = "1")
    private Boolean deleted;
    private String userType;  // teacher / student / academic_admin / department
}
