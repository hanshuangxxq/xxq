package com.xrq.xxq.module.user.service.login;

import com.xrq.xxq.module.user.dto.ChangePasswordRequest;
import com.xrq.xxq.module.user.dto.LoginRequest;
import com.xrq.xxq.module.user.dto.RegisterRequest;
import com.xrq.xxq.module.user.dto.UserSession;

/**
 * 唯一登录接口 —— 所有登录渠道的统一入口。
 */
public interface LoginService {

    UserSession login(LoginRequest request);

    UserSession refreshAccessToken(String refreshToken);

    Boolean logout(String tokenId);

    Boolean register(RegisterRequest request);

    Boolean changePassword(ChangePasswordRequest request);
}
