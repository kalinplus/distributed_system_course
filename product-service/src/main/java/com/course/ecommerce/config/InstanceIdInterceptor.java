package com.course.ecommerce.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 实例标识拦截器
 * 为每个响应添加 X-Instance-Id 头部，用于负载均衡验证
 */
@Configuration
public class InstanceIdInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(InstanceIdInterceptor.class);

    @Value("${server.port:8082}")
    private String port;

    @Value("${app.instance-id:}")
    private String instanceId;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String resolvedInstanceId = (instanceId == null || instanceId.isBlank())
                ? "product-service:" + port
                : instanceId;
        response.setHeader("X-Instance-Id", resolvedInstanceId);
        logger.info("Request handled by instance: {}", resolvedInstanceId);
        return true;
    }
}
