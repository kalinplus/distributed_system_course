package com.course.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.course.ecommerce.entity.Order;
import com.course.ecommerce.mapper.OrderMapper;
import com.course.ecommerce.service.OrderCancelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 订单取消服务实现类
 * 处理超时订单的自动取消逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancelServiceImpl implements OrderCancelService {

    private final StringRedisTemplate redisTemplate;
    private final OrderMapper orderMapper;
    private final RestTemplate restTemplate;

    @Value("${inventory.service.url:http://inventory-service:8084}")
    private String inventoryServiceUrl;

    private static final String CANCEL_IDEMPOTENCY_KEY_PREFIX = "cancel:";
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final long CANCEL_IDEMPOTENCY_EXPIRE_HOURS = 24;

    @Override
    public boolean cancelTimeoutOrder(String orderNo, Long userId, Long productId,
                                      Long seckillActivityId, Integer quantity) {
        log.info("Starting to cancel timeout order, orderNo: {}", orderNo);

        // 1. 幂等性检查：检查是否已处理过
        String cancelKey = CANCEL_IDEMPOTENCY_KEY_PREFIX + orderNo;
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(cancelKey, "1", Duration.ofHours(CANCEL_IDEMPOTENCY_EXPIRE_HOURS));

        if (Boolean.FALSE.equals(isNew)) {
            log.warn("Order already cancelled or being cancelled, orderNo: {}", orderNo);
            return false;
        }

        try {
            // 2. 更新订单状态为CANCELLED（使用乐观锁）
            LambdaUpdateWrapper<Order> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Order::getOrderNo, orderNo)
                    .eq(Order::getStatus, "PENDING")
                    .set(Order::getStatus, "CANCELLED");

            int updated = orderMapper.update(updateWrapper);

            if (updated == 0) {
                log.warn("Order status update failed, order may not be in PENDING status, orderNo: {}", orderNo);
                // 清理幂等性Key，允许重试
                redisTemplate.delete(cancelKey);
                return false;
            }

            log.info("Order status updated to CANCELLED, orderNo: {}", orderNo);

            // 3. 回滚Redis库存
            try {
                String url = String.format("%s/api/inventory/internal/rollback/%s/%s",
                        inventoryServiceUrl, productId, quantity);

                log.info("Calling inventory service to rollback stock, url: {}", url);
                ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);

                if (response.getStatusCode().is2xxSuccessful() && "OK".equals(response.getBody())) {
                    log.info("Stock rollback successful for orderNo: {}, productId: {}",
                            orderNo, productId);
                } else {
                    log.warn("Stock rollback returned unexpected response, orderNo: {}, status: {}, body: {}",
                            orderNo, response.getStatusCode(), response.getBody());
                }
            } catch (RestClientException e) {
                log.error("Failed to rollback stock for orderNo: {}, productId: {}, error: {}",
                        orderNo, productId, e.getMessage(), e);
                // 库存回滚失败不影响订单取消，记录日志即可
            }

            // 4. 清理幂等性Key（idempotency:{userId}:{productId}:{seckillActivityId}）
            String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + userId + ":" + productId + ":" + seckillActivityId;
            try {
                redisTemplate.delete(idempotencyKey);
                log.info("Idempotency key cleaned, key: {}", idempotencyKey);
            } catch (Exception e) {
                log.error("Failed to clean idempotency key: {}, error: {}", idempotencyKey, e.getMessage());
                // 清理失败不影响主流程
            }

            log.info("Order cancelled successfully, orderNo: {}", orderNo);
            return true;

        } catch (Exception e) {
            log.error("Error cancelling order, orderNo: {}, error: {}", orderNo, e.getMessage(), e);
            // 发生异常时清理cancel幂等性Key，允许重试
            redisTemplate.delete(cancelKey);
            throw e;
        }
    }
}
