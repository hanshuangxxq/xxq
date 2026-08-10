package com.xrq.xxq.module.user.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;

@Data
public class RegisterRequest {
    @NonNull
    private String account;
    @NonNull
    private String password;
    @NonNull
    private String userType;
    private String identifier;
    private Long collegeId;     // 院系管理员注册时所属院系 -> college.id（仅 department 类型使用）
}
