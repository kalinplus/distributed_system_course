package com.course.ecommerce.common.exception;

import com.course.ecommerce.common.Result;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一捕获异常，转换为标准响应格式，避免把堆栈信息暴露给前端
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 捕获业务异常（如用户名重复、密码错误） */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 捕获数据库唯一键冲突（并发注册同一用户名时触发），统一返回 409 */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<?> handleDuplicateKeyException(DuplicateKeyException e) {
        return Result.fail(409, "用户名已存在");
    }

    /** 捕获其他未预期的异常 */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        return Result.fail(500, "服务器内部错误：" + e.getMessage());
    }
}
