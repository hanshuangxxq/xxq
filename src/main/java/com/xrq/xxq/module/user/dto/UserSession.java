package com.xrq.xxq.module.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录状态存储信息
 */
@Data
public class UserSession {
    private Long userId;
    private String userType;
    private String name;
    private String account;
    private String avatar;
    private String role;
    private String accessToken;
    private String refreshToken;
    @JsonIgnore
    private String tokenId;
    private LocalDateTime loginTime;
    private LocalDateTime lastLoginTime;
}
