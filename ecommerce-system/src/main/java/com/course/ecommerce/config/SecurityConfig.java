package com.course.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置
 *
 * 目的：借用 BCryptPasswordEncoder 做密码加密，
 * 但关闭 Spring Security 的自动登录拦截（否则所有接口都会被拦截要求登录）。
 * 我们自己的 JWT 拦截逻辑在 AuthInterceptor 里实现。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 关闭 CSRF 保护，放行所有请求。
     * 我们的认证由 JWT 拦截器负责，不需要 Spring Security 的拦截。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * 密码加密器（BCrypt 算法）
     * 注册时用它加密，登录时用它验证。
     * 注入到 Service 中使用。
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
