package com.course.ecommerce.service.impl;

import com.course.ecommerce.common.exception.BusinessException;
import com.course.ecommerce.config.JwtUtil;
import com.course.ecommerce.dto.LoginRequest;
import com.course.ecommerce.dto.LoginResponse;
import com.course.ecommerce.dto.RegisterRequest;
import com.course.ecommerce.entity.User;
import com.course.ecommerce.mapper.UserMapper;
import com.course.ecommerce.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public User register(RegisterRequest request) {
        // 1. 入参校验
        if (request.getUsername() == null || request.getUsername().length() < 3) {
            throw new BusinessException(400, "用户名至少3个字符");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new BusinessException(400, "密码至少6个字符");
        }

        // 2. 检查用户名是否已存在
        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new BusinessException(409, "用户名已存在");
        }

        // 3. 构建用户，密码用 BCrypt 加密后存储
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(1);

        userMapper.insert(user);
        return user;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 入参校验
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new BusinessException(400, "用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException(400, "密码不能为空");
        }

        // 2. 查找用户
        User user = userMapper.findByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 3. 验证密码（BCrypt 比对）
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 4. 生成 JWT Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(token, user.getId(), user.getUsername());
    }
}
