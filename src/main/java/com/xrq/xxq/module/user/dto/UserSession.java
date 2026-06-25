package com.xrq.xxq.module.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserSession {
    private Long userId;
    private String userType;
    private String name;
    private String account;
    private String avatar;
    private String role;
    private String tokenId;
    private LocalDateTime loginTime;
}
