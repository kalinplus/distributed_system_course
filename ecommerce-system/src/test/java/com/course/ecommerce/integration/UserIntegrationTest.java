package com.course.ecommerce.integration;

import com.course.ecommerce.dto.LoginRequest;
import com.course.ecommerce.dto.RegisterRequest;
import com.course.ecommerce.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成冒烟测试：连接真实 MySQL，跑完整请求链路
 * 运行前确保 Docker 容器已启动（docker compose up -d）
 *
 * @AfterEach 清理测试数据，避免重复运行时用户名冲突
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    private static final String TEST_USERNAME = "integration_test_user";

    @AfterEach
    void cleanUp() {
        // 删除测试期间插入的用户，保证每次跑测试都是干净状态
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.course.ecommerce.entity.User> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.eq("username", TEST_USERNAME);
        userMapper.delete(wrapper);
    }

    @Test
    @DisplayName("冒烟测试：注册 → 登录完整流程")
    void smoke_register_then_login() throws Exception {
        // Step 1: 注册
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setUsername(TEST_USERNAME);
        registerReq.setPassword("password123");
        registerReq.setEmail("test@example.com");

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value(TEST_USERNAME));

        // Step 2: 登录
        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername(TEST_USERNAME);
        loginReq.setPassword("password123");

        MvcResult loginResult = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value(TEST_USERNAME))
                .andReturn();

        // 验证 token 不为空字符串
        String body = loginResult.getResponse().getContentAsString();
        assertThat(body).contains("token");
    }

    @Test
    @DisplayName("集成：重复注册同一用户名返回 409")
    void register_duplicate_username_returns_409() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword("password123");

        // 第一次注册
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(200));

        // 第二次注册同一用户名
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("集成：用错误密码登录返回 401")
    void login_wrong_password_returns_401() throws Exception {
        // 先注册
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setUsername(TEST_USERNAME);
        registerReq.setPassword("password123");
        mockMvc.perform(post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)));

        // 用错误密码登录
        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername(TEST_USERNAME);
        loginReq.setPassword("wrongpassword");

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
