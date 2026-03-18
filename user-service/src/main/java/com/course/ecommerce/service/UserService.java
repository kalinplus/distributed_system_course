package com.course.ecommerce.service;

import com.course.ecommerce.dto.LoginRequest;
import com.course.ecommerce.dto.LoginResponse;
import com.course.ecommerce.dto.RegisterRequest;
import com.course.ecommerce.entity.User;

/**
 * 用户服务接口
 * 定义"能做什么"，具体实现在 impl/UserServiceImpl.java
 */
public interface UserService {

    /**
     * 用户注册
     * @return 注册成功的用户信息（不含密码）
     */
    User register(RegisterRequest request);

    /**
     * 用户登录
     * @return 包含 JWT Token 的登录响应
     */
    LoginResponse login(LoginRequest request);
}
