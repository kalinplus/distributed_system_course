package com.course.ecommerce.common;

import lombok.Data;

/**
 * 统一响应格式
 * 所有接口都返回这个结构：{ code, message, data }
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功（带数据） */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /** 成功（无数据） */
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    /** 失败 */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
