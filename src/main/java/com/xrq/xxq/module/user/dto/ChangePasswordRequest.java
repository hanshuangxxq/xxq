package com.xrq.xxq.module.user.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;

@Data
public class ChangePasswordRequest {
    @NonNull
    private String account;
    @NonNull
    private String oldPassword;
    @NonNull
    private String newPassword;
}
