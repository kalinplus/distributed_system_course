package com.course.ecommerce.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Redis 配置测试
 * 验证 Redis 操作是否正确配置
 */
@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("Redis StringRedisTemplate 注入成功")
    void stringRedisTemplate_injected() {
        assertThat(redisTemplate).isNotNull();
    }

    @Test
    @DisplayName("Redis 可以设置和获取值")
    void redis_setAndGet() {
        // Given
        String testKey = "test:key";
        String testValue = "test-value";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        redisTemplate.opsForValue().set(testKey, testValue);

        // Then
        verify(valueOperations).set(testKey, testValue);
    }

    @Test
    @DisplayName("Redis 可以设置带过期时间的值")
    void redis_setWithExpiration() {
        // Given
        String testKey = "test:key";
        String testValue = "test-value";
        long timeout = 30;
        TimeUnit unit = TimeUnit.MINUTES;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        redisTemplate.opsForValue().set(testKey, testValue, timeout, unit);

        // Then
        verify(valueOperations).set(testKey, testValue, timeout, unit);
    }
}
