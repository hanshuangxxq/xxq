package com.xrq.xxq.module.user.controller;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.user.dto.LoginRequest;
import com.xrq.xxq.module.user.dto.RegisterRequest;
import com.xrq.xxq.module.user.dto.UserSession;
import com.xrq.xxq.module.user.service.login.LoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;

    @PostMapping("/login")
    public Result<UserSession> login(@RequestBody LoginRequest request) {
        UserSession session = loginService.login(request);
        if (session == null) {
            return Result.fail("登录失败");
        }
        return Result.ok(session);
    }

    @PostMapping("/register")
    public Result<Boolean> register(@RequestBody RegisterRequest request) {
        return Result.ok(loginService.register(request));
    }

    @PostMapping("/login/refresh")
    public Result<UserSession> refresh(@RequestParam String refreshToken) {
        UserSession session = loginService.refreshAccessToken(refreshToken);
        return Result.ok(session);
    }

    @PostMapping("/login/logout")
    public Result<Boolean> logout(@RequestParam String tokenId) {
        return Result.ok(loginService.logout(tokenId));
    }
}
