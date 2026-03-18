package com.course.ecommerce.service;

import com.course.ecommerce.entity.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 商品缓存服务
 * 实现 Cache-Aside 模式
 */
@Service
public class ProductCacheService {

    private static final String CACHE_KEY_PREFIX = "product:detail:";
    private static final long CACHE_TTL_MINUTES = 30;
    private static final long NULL_CACHE_TTL_MINUTES = 5;
    private static final String NULL_VALUE = "null";
    private static final int TTL_JITTER_MINUTES = 5;  // 缓存雪崩防护：TTL 随机抖动
    private final Random random = new Random();

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取商品缓存
     * Cache-Aside 模式：先查缓存，缓存未命中再查数据库
     */
    public Optional<Product> getProduct(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId cannot be null");
        }

        String cacheKey = CACHE_KEY_PREFIX + productId;
        String json = redisTemplate.opsForValue().get(cacheKey);

        if (json != null) {
            // 处理空值缓存（缓存穿透防护）
            if (NULL_VALUE.equals(json)) {
                return Optional.empty();
            }

            // 正常缓存命中
            try {
                Product product = objectMapper.readValue(json, Product.class);
                return Optional.of(product);
            } catch (Exception e) {
                // 缓存数据损坏，删除缓存
                redisTemplate.delete(cacheKey);
            }
        }

        // 缓存未命中，返回空（由调用方查数据库）
        return Optional.empty();
    }

    /**
     * 设置商品缓存
     * 带随机抖动（缓存雪崩防护）
     */
    public void setProduct(Product product) {
        String cacheKey = CACHE_KEY_PREFIX + product.getId();
        try {
            String json = objectMapper.writeValueAsString(product);
            long ttlWithJitter = calculateTtlWithJitter();
            redisTemplate.opsForValue().set(cacheKey, json, ttlWithJitter, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 缓存写入失败，记录日志但不抛出异常
        }
    }

    /**
     * 计算带随机抖动的 TTL
     * 缓存雪崩防护：避免大量缓存同时过期
     */
    private long calculateTtlWithJitter() {
        // 生成 [-5, +5] 分钟的随机偏移
        int jitter = random.nextInt(TTL_JITTER_MINUTES * 2 + 1) - TTL_JITTER_MINUTES;
        return CACHE_TTL_MINUTES + jitter;
    }

    /**
     * 删除商品缓存
     */
    public void deleteProduct(Long productId) {
        String cacheKey = CACHE_KEY_PREFIX + productId;
        redisTemplate.delete(cacheKey);
    }

    /**
     * 缓存空值（防止缓存穿透）
     * 对于不存在的商品，缓存一个空值，缩短 TTL
     */
    public void setNullValue(Long productId) {
        String cacheKey = CACHE_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(cacheKey, NULL_VALUE, NULL_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    }
}
