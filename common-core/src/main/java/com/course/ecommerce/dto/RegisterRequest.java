package com.course.ecommerce.dto;

import lombok.Data;

/**
 * 注册请求参数
 */
@Data
public class RegisterRequest {

    /** 用户名，3-20 字符 */
    private String username;

    /** 密码，6-20 字符（明文，传输后加密存储） */
    private String password;

    private String phone;

    private String email;
}
