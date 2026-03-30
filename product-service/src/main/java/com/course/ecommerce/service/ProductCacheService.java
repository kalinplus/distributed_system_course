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

    private static final String LOCK_KEY_PREFIX = "product:lock:";
    private static final long LOCK_EXPIRE_SECONDS = 10;

    /**
     * 获取互斥锁（SETNX），用于缓存击穿防护
     * @return true = 获取成功，当前线程负责回源; false = 其他线程已获取锁
     */
    public boolean acquireLock(Long productId) {
        String lockKey = LOCK_KEY_PREFIX + productId;
        try {
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 释放互斥锁
     */
    public void releaseLock(Long productId) {
        String lockKey = LOCK_KEY_PREFIX + productId;
        try {
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            // ignore
        }
    }

    private static final long LOGICAL_EXPIRE_MINUTES = 30;

    /**
     * 逻辑过期模式：获取缓存，判断是否过期
     * 返回 Optional 含义：
     *   - empty() = 缓存不存在，需回源
     *   - present + expired = 缓存已过期，需异步刷新
     *   - present + not expired = 缓存有效
     */
    public Optional<CacheData<Product>> getProductWithLogicExpire(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId cannot be null");
        }
        String cacheKey = CACHE_KEY_PREFIX + productId;
        String json = redisTemplate.opsForValue().get(cacheKey);

        if (json == null) {
            return Optional.empty(); // 缓存不存在
        }

        // 空值缓存命中（穿透防护）
        if (NULL_VALUE.equals(json)) {
            return Optional.of(new CacheData<>(null, Long.MAX_VALUE));
        }

        try {
            CacheData<Product> cached = objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructParametricType(CacheData.class, Product.class));
            return Optional.of(cached);
        } catch (Exception e) {
            redisTemplate.delete(cacheKey);
            return Optional.empty();
        }
    }

    /**
     * 设置逻辑过期的缓存（雪崩防护：TTL jitter 仍然保留）
     */
    public void setProductWithLogicExpire(Product product) {
        String cacheKey = CACHE_KEY_PREFIX + product.getId();
        try {
            long logicalExpire = System.currentTimeMillis() + LOGICAL_EXPIRE_MINUTES * 60 * 1000;
            CacheData<Product> cacheData = new CacheData<>(product, logicalExpire);
            String json = objectMapper.writeValueAsString(cacheData);
            // 物理 TTL = 逻辑过期时间 + 一定余量（如 5 分钟），确保数据不提前被 Redis 回收
            long physicalTtlMinutes = LOGICAL_EXPIRE_MINUTES + 5;
            redisTemplate.opsForValue().set(cacheKey, json, physicalTtlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * 逻辑过期缓存对象
     */
    public static class CacheData<T> {
        private T data;
        private long logicalExpireTime; // 逻辑过期时间戳（毫秒）

        public CacheData() {} // Jackson 反序列化需要

        public CacheData(T data, long logicalExpireTime) {
            this.data = data;
            this.logicalExpireTime = logicalExpireTime;
        }

        public T getData() { return data; }
        public void setData(T data) { this.data = data; }
        public long getLogicalExpireTime() { return logicalExpireTime; }
        public void setLogicalExpireTime(long logicalExpireTime) { this.logicalExpireTime = logicalExpireTime; }
        public boolean isExpired() { return System.currentTimeMillis() > logicalExpireTime; }
    }
}
