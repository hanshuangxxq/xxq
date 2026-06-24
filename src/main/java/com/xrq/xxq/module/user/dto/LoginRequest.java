package com.xrq.xxq.module.user.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;

import java.util.Map;

@Data
public class LoginRequest {
    @NonNull
    private String type;             // account / wechat / qq / alipay
    @NonNull
    private Map<String, Object> data;
}
