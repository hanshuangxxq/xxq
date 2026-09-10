package com.xrq.xxq.util.auth;

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
    /** 登录成功时的公网出口 IP（X-Forwarded-For 第一跳），供操作日志按「公网IP|内网IP」显示 */
    private String loginPublicIp;
    /** 登录成功时前端上报的内网 IP（X-Client-Private-IP）；获取失败为 null */
    private String loginPrivateIp;
}
