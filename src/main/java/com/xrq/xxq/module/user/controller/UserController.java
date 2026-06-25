package com.xrq.xxq.module.user.controller;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.user.dto.UpdateProfileRequest;
import com.xrq.xxq.module.user.dto.UserProfileResponse;
import com.xrq.xxq.module.user.service.UserService;
import com.xrq.xxq.module.user.service.avatar.AvatarService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AvatarService avatarService;

    @GetMapping("/profile")
    public Result<UserProfileResponse> getProfile(@RequestParam Long userId) {
        return Result.ok(userService.getProfile(userId));
    }

    @PutMapping("/profile")
    public Result<Boolean> updateProfile(@RequestParam Long userId, @RequestBody UpdateProfileRequest request) {
        return Result.ok(userService.updateProfile(userId, request));
    }

    @PostMapping("/avatar/upload")
    public Result<String> uploadAvatar(@RequestParam Long userId, @RequestParam MultipartFile file) throws IOException {
        return Result.ok(avatarService.saveAvatar(userId, file));
    }
}
