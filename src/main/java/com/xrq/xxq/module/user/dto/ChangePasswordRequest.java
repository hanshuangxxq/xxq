package com.xrq.xxq.module.user.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/**
 * 修改密码接口类
 */
@Data
public class ChangePasswordRequest {
    @NonNull
    private String account;
    @NonNull
    private String oldPassword;
    @NonNull
    private String newPassword;
}
