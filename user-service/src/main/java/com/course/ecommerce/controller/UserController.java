package com.course.ecommerce.controller;

import com.course.ecommerce.common.Result;
import com.course.ecommerce.dto.LoginRequest;
import com.course.ecommerce.dto.RegisterRequest;
import com.course.ecommerce.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口
 * POST /api/user/register  注册
 * POST /api/user/login     登录
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Result<?> register(@RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @PostMapping("/login")
    public Result<?> login(@RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }
}
