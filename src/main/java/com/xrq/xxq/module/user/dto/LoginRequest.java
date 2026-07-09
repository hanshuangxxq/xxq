package com.xrq.xxq.module.user.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * 登录的接口类
 */
@Data
public class LoginRequest {
    @NonNull
    private String type;             // account / wechat / qq / alipay
    @NonNull
    private Map<String, Object> data;
}
