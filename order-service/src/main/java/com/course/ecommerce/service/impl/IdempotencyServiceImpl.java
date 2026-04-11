package com.course.ecommerce.service.impl;

import com.course.ecommerce.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性服务实现类
 * 基于Redis存储幂等性Key状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Key前缀
     */
    private static final String KEY_PREFIX = "idempotency:";

    /**
     * 过期时间：24小时
     */
    private static final long EXPIRE_HOURS = 24;

    /**
     * 状态：处理中
     */
    private static final String STATUS_PROCESSING = "PROCESSING";

    /**
     * 状态：已完成
     */
    private static final String STATUS_COMPLETED = "COMPLETED";

    /**
     * 分隔符
     */
    private static final String SEPARATOR = ":";

    @Override
    public boolean isDuplicate(String idempotencyKey) {
        String key = buildKey(idempotencyKey);
        String value = stringRedisTemplate.opsForValue().get(key);
        boolean isDup = value != null;
        if (isDup) {
            log.info("Duplicate request detected, key: {}, value: {}", idempotencyKey, value);
        }
        return isDup;
    }

    @Override
    public boolean markProcessing(String idempotencyKey, String orderNo) {
        String key = buildKey(idempotencyKey);
        String value = STATUS_PROCESSING + SEPARATOR + orderNo;

        // 使用setIfAbsent实现原子性检查并设置
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofHours(EXPIRE_HOURS));

        if (Boolean.TRUE.equals(success)) {
            log.info("Marked as PROCESSING, key: {}, orderNo: {}", idempotencyKey, orderNo);
            return true;
        } else {
            log.warn("Failed to mark PROCESSING, key already exists: {}", idempotencyKey);
            return false;
        }
    }

    @Override
    public void markCompleted(String idempotencyKey) {
        String key = buildKey(idempotencyKey);
        String currentValue = stringRedisTemplate.opsForValue().get(key);

        if (currentValue != null && currentValue.startsWith(STATUS_PROCESSING)) {
            // 提取订单号
            String orderNo = currentValue.substring(STATUS_PROCESSING.length() + SEPARATOR.length());
            String newValue = STATUS_COMPLETED + SEPARATOR + orderNo;

            stringRedisTemplate.opsForValue().set(key, newValue, Duration.ofHours(EXPIRE_HOURS));
            log.info("Marked as COMPLETED, key: {}, orderNo: {}", idempotencyKey, orderNo);
        } else {
            log.warn("Cannot mark COMPLETED, key not found or not in PROCESSING state: {}", idempotencyKey);
        }
    }

    @Override
    public String getOrderNo(String idempotencyKey) {
        String key = buildKey(idempotencyKey);
        String value = stringRedisTemplate.opsForValue().get(key);

        if (value == null) {
            return null;
        }

        // 解析订单号
        int separatorIndex = value.indexOf(SEPARATOR);
        if (separatorIndex > 0 && separatorIndex < value.length() - 1) {
            return value.substring(separatorIndex + 1);
        }

        return null;
    }

    /**
     * 构建Redis Key
     *
     * @param idempotencyKey 幂等性Key
     * @return Redis Key
     */
    private String buildKey(String idempotencyKey) {
        return KEY_PREFIX + idempotencyKey;
    }
}
