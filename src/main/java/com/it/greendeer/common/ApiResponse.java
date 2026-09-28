package com.it.greendeer.common;

/**
 * 统一 REST 响应包装。
 *
 * @param code    业务码：0 表示成功
 * @param message 提示信息
 * @param data    负载
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static ApiResponse<Void> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
