package com.course.ecommerce.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存雪崩测试
 * 验证 TTL 随机抖动策略
 */
class ProductCacheAvalancheTest {

    private static final int BASE_TTL_MINUTES = 30;
    private static final int JITTER_MINUTES = 5;
    private final Random random = new Random();

    /**
     * 测试 TTL 随机抖动
     * 验证缓存过期时间在基础 TTL ± 抖动范围内
     */
    @Test
    @DisplayName("缓存雪崩：TTL 随机抖动")
    void ttlRandomJitter() {
        // When
        long ttl = calculateTtlWithJitter();

        // Then
        long minTtl = BASE_TTL_MINUTES - JITTER_MINUTES;
        long maxTtl = BASE_TTL_MINUTES + JITTER_MINUTES;

        // TTL 应该在 [25, 35] 范围内
        assertThat(ttl).isGreaterThanOrEqualTo(minTtl);
        assertThat(ttl).isLessThanOrEqualTo(maxTtl);
    }

    /**
     * 计算带随机抖动的 TTL
     * 缓存雪崩防护：随机偏移量避免大量缓存同时过期
     */
    private long calculateTtlWithJitter() {
        // 生成 [-5, +5] 分钟的随机偏移
        int jitter = random.nextInt(JITTER_MINUTES * 2 + 1) - JITTER_MINUTES;
        return BASE_TTL_MINUTES + jitter;
    }

    /**
     * 验证 TTL 转换正确
     */
    @Test
    @DisplayName("缓存雪崩：TTL 转换为毫秒正确")
    void ttlConvertedToMilliseconds() {
        // Given
        long ttlMinutes = 30;

        // When
        long ttlMillis = TimeUnit.MINUTES.toMillis(ttlMinutes);

        // Then
        assertThat(ttlMillis).isEqualTo(30 * 60 * 1000);
    }
}
