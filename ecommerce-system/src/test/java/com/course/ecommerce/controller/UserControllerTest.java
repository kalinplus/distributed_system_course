package com.course.ecommerce.controller;

import com.course.ecommerce.common.exception.BusinessException;
import com.course.ecommerce.config.SecurityConfig;
import com.course.ecommerce.dto.LoginRequest;
import com.course.ecommerce.dto.LoginResponse;
import com.course.ecommerce.dto.RegisterRequest;
import com.course.ecommerce.entity.User;
import com.course.ecommerce.mapper.UserMapper;
import com.course.ecommerce.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController 层测试
 * 只加载 Web 层（Controller + ExceptionHandler），Service 用 Mock 替换
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    // @WebMvcTest 只加载 Web 层，但 @MapperScan 会注册 UserMapper。
    // 用 @MockBean 提供一个假的实现，避免 MyBatis 找不到 SqlSessionFactory 报错。
    @MockBean
    private UserMapper userMapper;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== 注册接口 ====================

    @Test
    @DisplayName("POST /api/user/register 注册成功，返回用户信息")
    void register_returns_user_on_success() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setStatus(1);

        when(userService.register(any(RegisterRequest.class))).thenReturn(user);

        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setPassword("password123");

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    @DisplayName("POST /api/user/register 用户名已存在，响应 code=409")
    void register_returns_409_on_duplicate_username() throws Exception {
        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new BusinessException(409, "用户名已存在"));

        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setPassword("password123");

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    @DisplayName("POST /api/user/register 参数校验失败，响应 code=400")
    void register_returns_400_on_invalid_input() throws Exception {
        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new BusinessException(400, "用户名至少3个字符"));

        RegisterRequest req = new RegisterRequest();
        req.setUsername("ab");
        req.setPassword("password123");

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 登录接口 ====================

    @Test
    @DisplayName("POST /api/user/login 登录成功，响应包含 token")
    void login_returns_token_on_success() throws Exception {
        LoginResponse response = new LoginResponse("mock.jwt.token", 1L, "testuser");
        when(userService.login(any(LoginRequest.class))).thenReturn(response);

        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setPassword("password123");

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("mock.jwt.token"))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    @DisplayName("POST /api/user/login 密码错误，响应 code=401")
    void login_returns_401_on_wrong_password() throws Exception {
        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(401, "用户名或密码错误"));

        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setPassword("wrongpass");

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("POST /api/user/login 用户不存在，响应 code=401")
    void login_returns_401_on_user_not_found() throws Exception {
        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(401, "用户名或密码错误"));

        LoginRequest req = new LoginRequest();
        req.setUsername("nobody");
        req.setPassword("password123");

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
