package com.course.ecommerce.common.exception;

/**
 * 业务异常
 * 当业务逻辑不满足时抛出（如用户名已存在、密码错误等）
 * 区别于系统异常（NullPointerException 等）
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
