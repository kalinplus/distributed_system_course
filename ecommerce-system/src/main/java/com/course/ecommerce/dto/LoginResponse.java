package com.course.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应数据
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    /** JWT Token，前端后续请求在 Header 里带上：Authorization: Bearer <token> */
    private String token;

    private Long userId;

    private String username;
}
