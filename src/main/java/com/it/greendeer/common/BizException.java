package com.it.greendeer.common;

/**
 * 业务异常：由全局异常处理器统一转为 ApiResponse。
 */
public class BizException extends RuntimeException {

    /** HTTP 状态码，默认 400。 */
    private final int status;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
