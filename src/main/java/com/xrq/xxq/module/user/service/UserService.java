package com.xrq.xxq.module.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xrq.xxq.module.user.dto.UpdateProfileRequest;
import com.xrq.xxq.module.user.dto.UserProfileResponse;
import com.xrq.xxq.module.user.entity.User;

public interface UserService extends IService<User> {

    UserProfileResponse getProfile(Long userId);

    boolean updateProfile(Long userId, UpdateProfileRequest request);
}
