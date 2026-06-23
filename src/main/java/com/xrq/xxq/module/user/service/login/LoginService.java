package com.xrq.xxq.module.user.service.login;

import com.xrq.xxq.module.user.dto.LoginRequest;
import com.xrq.xxq.module.user.dto.UserSession;

/**
 * 唯一登录接口 —— 所有登录渠道的统一入口。
 */
public interface LoginService {

    /**
     * 登录的接口方法
     * @param request
     * @return
     */
    UserSession login(LoginRequest request);

    /**
     * 刷新访问令牌的接口方法
     * @param refreshToken
     * @return
     */
    UserSession refreshAccessToken(String refreshToken);

    /**
     * 登出的接口方法
     * @param tokenId
     * @return
     */
    Boolean logout(String tokenId);
}
