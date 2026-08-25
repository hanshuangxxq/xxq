package com.xrq.xxq.module.preference.controller;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.preference.service.UserPreferenceService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 用户个性化偏好接口。任何已登录用户读写/重置自己的偏好（无跨用户/管理员接口）。
 */
@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;
    private final AuthFacade authFacade;

    /**
     * 获取当前用户偏好；从未保存过（或刚重置）返回 {}。
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> myPrefs(HttpServletRequest request) {
        return Result.ok(userPreferenceService.getPrefs(authFacade.currentUserId(request)));
    }

    /**
     * 顶层浅合并当前用户偏好：key 存在则更新、不存在则添加、value 为 null 则删除该 key。
     * 返回合并后的完整偏好。
     */
    @PutMapping("/me")
    public Result<Map<String, Object>> mergeMyPrefs(HttpServletRequest request,
                                                 @RequestBody Map<String, Object> delta) {
        return Result.ok(userPreferenceService.mergePrefs(authFacade.currentUserId(request), delta));
    }

    /**
     * 重置当前用户的全部个性化设置，回到系统默认。幂等。
     */
    @DeleteMapping("/me")
    public Result<Void> resetMyPrefs(HttpServletRequest request) {
        userPreferenceService.resetPrefs(authFacade.currentUserId(request));
        return Result.ok();
    }
}
