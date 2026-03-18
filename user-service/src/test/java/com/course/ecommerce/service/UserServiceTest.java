package com.course.ecommerce.service;

import com.course.ecommerce.common.exception.BusinessException;
import com.course.ecommerce.config.JwtUtil;
import com.course.ecommerce.dto.LoginRequest;
import com.course.ecommerce.dto.LoginResponse;
import com.course.ecommerce.dto.RegisterRequest;
import com.course.ecommerce.entity.User;
import com.course.ecommerce.mapper.UserMapper;
import com.course.ecommerce.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试
 * 不启动 Spring 容器，用 Mockito 替换所有外部依赖（数据库、JWT）
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserServiceImpl userService;

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;
    private User existingUser;

    @BeforeEach
    void setUp() {
        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setUsername("testuser");
        validRegisterRequest.setPassword("password123");

        validLoginRequest = new LoginRequest();
        validLoginRequest.setUsername("testuser");
        validLoginRequest.setPassword("password123");

        existingUser = new User();
        existingUser.setId(1L);
        existingUser.setUsername("testuser");
        existingUser.setPasswordHash("$2a$10$hashedpassword");
        existingUser.setStatus(1);
    }

    // ==================== 注册测试 ====================

    @Test
    @DisplayName("注册成功：返回用户信息，密码已加密存储")
    void register_success() {
        when(userMapper.findByUsername("testuser")).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedpassword");
        when(userMapper.insert(any(User.class))).thenReturn(1);

        User result = userService.register(validRegisterRequest);

        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getPasswordHash()).isEqualTo("$2a$10$hashedpassword");
        assertThat(result.getStatus()).isEqualTo(1);
        verify(userMapper).insert(any(User.class));
    }

    @Test
    @DisplayName("注册失败：用户名已存在，抛出 409")
    void register_fail_username_exists() {
        when(userMapper.findByUsername("testuser")).thenReturn(existingUser);

        assertThatThrownBy(() -> userService.register(validRegisterRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在")
                .extracting("code")
                .isEqualTo(409);

        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("注册失败：用户名少于3个字符，抛出 400")
    void register_fail_username_too_short() {
        validRegisterRequest.setUsername("ab");

        assertThatThrownBy(() -> userService.register(validRegisterRequest))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("注册失败：密码少于6个字符，抛出 400")
    void register_fail_password_too_short() {
        validRegisterRequest.setPassword("123");

        assertThatThrownBy(() -> userService.register(validRegisterRequest))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("注册失败：用户名为空，抛出 400")
    void register_fail_username_null() {
        validRegisterRequest.setUsername(null);

        assertThatThrownBy(() -> userService.register(validRegisterRequest))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("注册失败：密码为空，抛出 400")
    void register_fail_password_null() {
        validRegisterRequest.setPassword(null);

        assertThatThrownBy(() -> userService.register(validRegisterRequest))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    // ==================== 登录测试 ====================

    @Test
    @DisplayName("登录成功：返回含 Token 的响应")
    void login_success() {
        when(userMapper.findByUsername("testuser")).thenReturn(existingUser);
        when(passwordEncoder.matches("password123", "$2a$10$hashedpassword")).thenReturn(true);
        when(jwtUtil.generateToken(1L, "testuser")).thenReturn("mock.jwt.token");

        LoginResponse result = userService.login(validLoginRequest);

        assertThat(result.getToken()).isEqualTo("mock.jwt.token");
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("登录失败：用户不存在，抛出 401")
    void login_fail_user_not_found() {
        when(userMapper.findByUsername("testuser")).thenReturn(null);

        assertThatThrownBy(() -> userService.login(validLoginRequest))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("登录失败：密码错误，抛出 401")
    void login_fail_wrong_password() {
        when(userMapper.findByUsername("testuser")).thenReturn(existingUser);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> userService.login(validLoginRequest))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(401);
    }
}
