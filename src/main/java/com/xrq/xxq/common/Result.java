package com.xrq.xxq.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @类名 Result
 * @Date 2026/6/5
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    /**
     * 需要返回数据
     * @param data
     * @return
     * @param <T>
     */
    public static <T> Result<T> ok (T data) {
        return new Result<>(200, "success", data);
    }

    /**
     * 只代表成功i
     * @return
     * @param <T>
     */
    public static <T> Result<T> ok () {
        return new Result<>(200, "success", null);
    }

    /**
     * 带数据和消息的成功返回
     * @param message
     * @param data
     * @return
     * @param <T>
     */
    public static <T> Result<T> ok (String message, T data) {
        return new Result<>(200, message, data);
    }

    /**
     * 失败码和消息
     * @param code
     * @param message
     * @return
     * @param <T>
     */
    public static <T> Result<T> fail (Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 服务器原因导致的失败，返回码和消息
     * @param message
     * @return
     * @param <T>
     */
    public static <T> Result<T> fail (String message) {
        return new Result<>(500, message, null);
    }

    /**
     * 未授权返回码和消息
     * @return
     * @param <T>
     */
    public static <T> Result<T> unauthorized () {
        return new Result<>(401, "请先登录", null);
    }

}
