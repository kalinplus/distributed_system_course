package com.course.ecommerce.service;

import com.course.ecommerce.entity.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 缓存穿透测试
 * 验证空值缓存和参数校验
 */
@ExtendWith(MockitoExtension.class)
class ProductCachePenetrationTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ProductCacheService productCacheService;

    /**
     * 测试缓存空值：对于不存在的商品，缓存空值
     */
    @Test
    @DisplayName("缓存空值：不存在商品也缓存")
    void cacheNullValue_forNonExistentProduct() {
        // Given
        Long productId = 999L;
        String cacheKey = "product:detail:" + productId;
        String nullValue = "null";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When - 缓存空值
        productCacheService.setNullValue(productId);

        // Then
        verify(valueOperations).set(eq(cacheKey), eq(nullValue), eq(5L), eq(TimeUnit.MINUTES));
    }

    /**
     * 测试缓存空值被正确识别
     */
    @Test
    @DisplayName("读取到空值缓存时返回空Optional")
    void getProduct_returnsEmptyForNullValue() {
        // Given
        Long productId = 999L;
        String cacheKey = "product:detail:" + productId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn("null");  // 空值缓存

        // When
        Optional<Product> result = productCacheService.getProduct(productId);

        // Then
        assertThat(result).isEmpty();
    }

    /**
     * 测试产品 ID 参数校验
     */
    @Test
    @DisplayName("产品 ID 为空时抛出异常")
    void productIdNull_throwsException() {
        // Given
        Long nullId = null;

        // When/Then
        try {
            productCacheService.getProduct(nullId);
            // Should not reach here
            assertThat(false).isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("productId");
        }
    }
}
