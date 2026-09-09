package com.xrq.xxq.common;

/**
 * 限流异常：触发防抖/限流规则时抛出，固定 code 429。
 * 与 BusinessException 区分子类，GlobalExceptionHandler 对其不记日志 ——
 * 首次触限已由 RateLimitService 记入 rate-limit 专用日志文件，
 * 窗口内后续拒绝完全静默，防止攻击流量刷爆 warn 日志。
 */
public class RateLimitException extends BusinessException {

    public RateLimitException(String message) {
        super(429, message);
    }
}
