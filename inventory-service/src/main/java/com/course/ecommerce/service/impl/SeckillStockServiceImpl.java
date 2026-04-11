package com.course.ecommerce.service.impl;

import com.course.ecommerce.service.SeckillStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀库存服务实现类
 * 使用Redis Lua脚本实现原子扣减
 */
@Service
public class SeckillStockServiceImpl implements SeckillStockService {

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String DEDUP_KEY_PREFIX = "seckill:dup:";
    private static final int DEFAULT_EXPIRE_SECONDS = 3600; // 默认1小时

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<Long> stockDeductScript;

    @PostConstruct
    public void init() {
        stockDeductScript = new DefaultRedisScript<>();
        stockDeductScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("scripts/stock_deduct.lua")));
        stockDeductScript.setResultType(Long.class);
    }

    @Override
    public void warmupStock(Long productId, Long stock, Integer expireSeconds) {
        if (productId == null || stock == null || stock < 0) {
            throw new IllegalArgumentException("Invalid productId or stock");
        }

        String stockKey = STOCK_KEY_PREFIX + productId;
        int expire = expireSeconds != null ? expireSeconds : DEFAULT_EXPIRE_SECONDS;

        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(stock), expire, TimeUnit.SECONDS);
    }

    @Override
    public Long deductStock(Long userId, Long productId, Long activityId, Integer quantity) {
        if (userId == null || productId == null || activityId == null || quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Invalid parameters");
        }

        String stockKey = STOCK_KEY_PREFIX + productId;
        String dupKey = DEDUP_KEY_PREFIX + userId + ":" + productId + ":" + activityId;

        try {
            Long result = stringRedisTemplate.execute(
                    stockDeductScript,
                    Collections.singletonList(stockKey),
                    String.valueOf(quantity),
                    String.valueOf(DEFAULT_EXPIRE_SECONDS)
            );
            return result != null ? result : -3L;
        } catch (Exception e) {
            // 执行失败，返回库存未初始化
            return -3L;
        }
    }

    @Override
    public void rollbackStock(Long productId, Integer quantity) {
        if (productId == null || quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Invalid productId or quantity");
        }

        String stockKey = STOCK_KEY_PREFIX + productId;

        try {
            // 使用incrby回滚库存
            stringRedisTemplate.opsForValue().increment(stockKey, quantity.longValue());
        } catch (Exception e) {
            // 回滚失败，记录日志但不抛出异常
        }
    }

    @Override
    public Long getStock(Long productId) {
        if (productId == null) {
            return null;
        }

        String stockKey = STOCK_KEY_PREFIX + productId;
        String stockStr = stringRedisTemplate.opsForValue().get(stockKey);

        if (stockStr == null) {
            return null;
        }

        try {
            return Long.parseLong(stockStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void clearDedupKey(Long userId, Long productId, Long activityId) {
        if (userId == null || productId == null || activityId == null) {
            return;
        }

        String dupKey = DEDUP_KEY_PREFIX + userId + ":" + productId + ":" + activityId;
        stringRedisTemplate.delete(dupKey);
    }
}
