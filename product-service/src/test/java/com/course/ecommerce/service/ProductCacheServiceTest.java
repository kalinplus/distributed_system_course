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
 * 缓存服务测试 - Cache-Aside 模式
 * 测试缓存读取、缓存写入、缓存命中/未命中
 */
@ExtendWith(MockitoExtension.class)
class ProductCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ProductCacheService productCacheService;

    /**
     * 测试缓存命中时，直接返回缓存数据
     */
    @Test
    @DisplayName("缓存命中：直接返回缓存数据")
    void cacheHit_returnFromCache() throws Exception {
        // Given
        Long productId = 1L;
        String cacheKey = "product:detail:" + productId;

        Product cachedProduct = createTestProduct(productId);
        String jsonProduct = objectMapper.writeValueAsString(cachedProduct);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(jsonProduct);  // 缓存命中

        // When
        Optional<Product> result = productCacheService.getProduct(productId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(productId);
        assertThat(result.get().getName()).isEqualTo("Test Product");
    }

    /**
     * 测试缓存未命中时，返回空Optional
     */
    @Test
    @DisplayName("缓存未命中：返回空Optional")
    void cacheMiss_returnEmpty() {
        // Given
        Long productId = 1L;
        String cacheKey = "product:detail:" + productId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);  // 缓存未命中

        // When
        Optional<Product> result = productCacheService.getProduct(productId);

        // Then
        assertThat(result).isEmpty();
    }

    /**
     * 测试写入缓存
     */
    @Test
    @DisplayName("写入缓存成功")
    void writeCache_success() throws Exception {
        // Given
        Long productId = 1L;
        String cacheKey = "product:detail:" + productId;
        Product product = createTestProduct(productId);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        productCacheService.setProduct(product);

        // Then - 使用 anyLong() 因为 TTL 有随机抖动
        verify(valueOperations).set(eq(cacheKey), anyString(), anyLong(), eq(TimeUnit.MINUTES));
    }

    /**
     * 测试删除缓存
     */
    @Test
    @DisplayName("删除缓存成功")
    void deleteCache_success() {
        // Given
        Long productId = 1L;
        String cacheKey = "product:detail:" + productId;

        when(redisTemplate.delete(cacheKey)).thenReturn(true);

        // When
        productCacheService.deleteProduct(productId);

        // Then
        verify(redisTemplate).delete(cacheKey);
    }

    private Product createTestProduct(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("Test Product");
        product.setDescription("Test Description");
        product.setPrice(new BigDecimal("99.99"));
        product.setStock(100);
        product.setStatus(1);
        return product;
    }
}
