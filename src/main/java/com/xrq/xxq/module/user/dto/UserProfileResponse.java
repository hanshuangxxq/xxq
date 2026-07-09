package com.xrq.xxq.module.user.dto;

import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.user.entity.GenderEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户资料
 */
@Data
public class UserProfileResponse {
    private Long userId;
    private String name;
    private String email;
    private String phone;
    private GenderEnum gender;
    private String avatar;
    private String description;
    private String role;
    private String userType;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private Integer status;

    private String identifier;
    private String grade;
    private String major;
    private ClassName className;
    private Integer enrollmentYear;
    private String title;
    private String department;
    private String position;
}
