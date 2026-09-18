package com.xrq.xxq.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 限流拒绝：不记日志（首次触限已由 RateLimitService 记入 rate-limit 专用文件，
     * 此处再记会让攻击者刷爆 warn 日志），仅返回 429。
     */
    @ExceptionHandler(RateLimitException.class)
    public Result<Void> handleRateLimit(RateLimitException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常 — code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数校验失败 — message={}", e.getMessage());
        return Result.fail(400, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误 — message={}", e.getMessage());
        return Result.fail(400, "请求体格式错误");
    }

    /**
     * 上传文件超过 multipart 硬上限（spring.servlet.multipart.max-file-size）：
     * Spring 在 DispatcherServlet 解析阶段就抛出，早于 Controller 与业务层的大小校验，
     * 此处兜底转 400，避免落到通用 handler 变成无意义的「服务器内部错误」。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件超限 — message={}", e.getMessage());
        return Result.fail(400, "上传文件过大，请压缩后重试");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("服务器内部错误 — message={}", e.getMessage());
        return Result.fail(500, "服务器内部错误，请联系管理员");
    }
}
