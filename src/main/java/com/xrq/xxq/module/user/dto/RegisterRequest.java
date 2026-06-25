package com.xrq.xxq.module.user.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;

@Data
public class RegisterRequest {
    @NonNull
    private String account;
    @NonNull
    private String password;
    @NonNull
    private String userType;
    private String identifier;
}
