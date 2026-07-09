package com.xrq.xxq.module.user.dto;

import com.xrq.xxq.module.user.entity.GenderEnum;
import lombok.Data;

/**
 * 更新用户资料请求类
 */
@Data
public class UpdateProfileRequest {
    private String email;
    private String phone;
    private GenderEnum gender;
    private String avatar;
    private String description;
}
