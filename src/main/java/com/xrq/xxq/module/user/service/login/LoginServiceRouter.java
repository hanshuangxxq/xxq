package com.xrq.xxq.module.user.service.login;

import com.xrq.xxq.module.user.dto.LoginRequest;
import com.xrq.xxq.module.user.dto.RegisterRequest;
import com.xrq.xxq.module.user.dto.UserSession;
import com.xrq.xxq.module.user.service.login.impl.AccountLoginService;
import com.xrq.xxq.module.user.service.login.impl.AlipayLoginService;
import com.xrq.xxq.module.user.service.login.impl.QQLoginService;
import com.xrq.xxq.module.user.service.login.impl.WeChatLoginService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 登录路由器 —— 根据 type 字段分发到对应的渠道实现类。
 * 标注 {@link Primary}，作为 LoginService 的默认注入实现。
 */
@Primary
@Service
public class LoginServiceRouter implements LoginService {

    private final AccountLoginService accountLoginService;
    private final WeChatLoginService weChatLoginService;
    private final QQLoginService qqLoginService;
    private final AlipayLoginService alipayLoginService;

    public LoginServiceRouter(AccountLoginService accountLoginService,
                              WeChatLoginService weChatLoginService,
                              QQLoginService qqLoginService,
                              AlipayLoginService alipayLoginService) {
        this.accountLoginService = accountLoginService;
        this.weChatLoginService = weChatLoginService;
        this.qqLoginService = qqLoginService;
        this.alipayLoginService = alipayLoginService;
    }

    @Override
    public UserSession login(LoginRequest request) {
        return switch (request.getType()) {
            case "account" -> accountLoginService.login(request);
            case "wechat"  -> weChatLoginService.login(request);
            case "qq"      -> qqLoginService.login(request);
            case "alipay"  -> alipayLoginService.login(request);
            default        -> throw new IllegalArgumentException("不支持的登录方式: " + request.getType());
        };
    }

    @Override
    public UserSession refreshAccessToken(String refreshToken) {
        return accountLoginService.refreshAccessToken(refreshToken);
    }

    @Override
    public Boolean logout(String tokenId) {
        return accountLoginService.logout(tokenId);
    }

    @Override
    public Boolean register(RegisterRequest request) {
        return accountLoginService.register(request);
    }
}
