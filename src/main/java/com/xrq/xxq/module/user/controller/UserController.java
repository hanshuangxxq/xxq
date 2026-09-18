package com.xrq.xxq.module.user.controller;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.user.dto.UpdateProfileRequest;
import com.xrq.xxq.module.user.dto.UserProfileResponse;
import com.xrq.xxq.module.user.service.UserService;
import com.xrq.xxq.module.user.service.avatar.AvatarService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireLogin;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@RequireLogin
public class UserController {

    private final UserService userService;
    private final AvatarService avatarService;
    private final AuthFacade authFacade;

    @GetMapping("/profile")
    public Result<UserProfileResponse> getProfile(@RequestParam Long userId, @RequestParam(required = false) String tokenId) {
        return Result.ok(userService.getProfile(userId, tokenId));
    }

    @PutMapping("/profile")
    public Result<Boolean> updateProfile(@RequestParam Long userId, @RequestBody UpdateProfileRequest request) {
        return Result.ok(userService.updateProfile(userId, request));
    }

    /**
     * 上传头像。
     * <p><b>归属校验</b>：{@code userId} 是历史遗留的 query 参数，此前未做归属校验，
     * 任意登录用户传 {@code userId=他人} 即可覆盖他人头像。现在传了就必须等于当前登录用户，
     * 否则 403；不传则回退当前登录用户（兼容尚未改造的旧前端）。
     */
    @PostMapping("/avatar/upload")
    public Result<String> uploadAvatar(HttpServletRequest request,
                                       @RequestParam(required = false) Long userId,
                                       @RequestParam MultipartFile file) throws IOException {
        Long current = authFacade.currentUserId(request);
        if (userId != null && !userId.equals(current)) {
            throw new BusinessException(403, "权限不足");
        }
        return Result.ok(avatarService.saveAvatar(current, file));
    }
}
