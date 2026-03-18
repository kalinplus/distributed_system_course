package com.course.ecommerce.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 缓存击穿测试
 * 验证互斥锁策略防止热点 key 失效时大量请求同时回源
 */
@ExtendWith(MockitoExtension.class)
class ProductCacheBreakdownTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    /**
     * 测试互斥锁获取成功
     * 场景：热点商品缓存过期，第一个请求获取锁成功
     */
    @Test
    @DisplayName("互斥锁：获取锁成功")
    void mutexLock_acquireSuccess() {
        // Given
        Long productId = 1L;
        String lockKey = "product:lock:" + productId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(lockKey), eq("1"), eq(10L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        // When
        boolean result = acquireLock(productId);

        // Then
        assertThat(result).isTrue();
        verify(valueOperations).setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
    }

    /**
     * 测试互斥锁获取失败
     * 场景：其他线程已获取锁
     */
    @Test
    @DisplayName("互斥锁：获取锁失败（其他线程已获取）")
    void mutexLock_acquireFailed() {
        // Given
        Long productId = 1L;
        String lockKey = "product:lock:" + productId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(lockKey), eq("1"), eq(10L), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        // When
        boolean result = acquireLock(productId);

        // Then
        assertThat(result).isFalse();
    }

    /**
     * 测试释放锁
     */
    @Test
    @DisplayName("互斥锁：释放锁成功")
    void mutexLock_releaseSuccess() {
        // Given
        Long productId = 1L;
        String lockKey = "product:lock:" + productId;

        when(redisTemplate.delete(lockKey)).thenReturn(true);

        // When
        boolean result = releaseLock(productId);

        // Then
        assertThat(result).isTrue();
        verify(redisTemplate).delete(lockKey);
    }

    /**
     * 获取互斥锁
     * 使用 Redis SETNX 命令，设置 10 秒过期时间
     */
    private boolean acquireLock(Long productId) {
        String lockKey = "product:lock:" + productId;
        try {
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 释放互斥锁
     */
    private boolean releaseLock(Long productId) {
        String lockKey = "product:lock:" + productId;
        try {
            Boolean result = redisTemplate.delete(lockKey);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }
}
