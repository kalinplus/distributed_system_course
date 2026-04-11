package com.course.ecommerce.controller;

import com.course.ecommerce.common.Result;
import com.course.ecommerce.dto.SeckillOrderRequest;
import com.course.ecommerce.event.OrderCreateEvent;
import com.course.ecommerce.producer.SeckillOrderProducer;
import com.course.ecommerce.service.IdempotencyService;
import com.course.ecommerce.util.IdGenUtil;
import com.course.ecommerce.util.IdempotencyKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 秒杀订单接口
 * POST /api/orders/seckill - 秒杀下单
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class SeckillController {

    private final IdempotencyService idempotencyService;
    private final SeckillOrderProducer seckillOrderProducer;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${inventory.service.url:http://inventory-service:8084}")
    private String inventoryServiceUrl;

    /**
     * 秒杀下单接口
     *
     * @param userId  用户ID (请求头 X-User-Id)
     * @param request 秒杀订单请求
     * @return 202 Accepted - 排队中; 409 Conflict - 重复请求; 410 Gone - 库存不足或已参与
     */
    @PostMapping("/seckill")
    public ResponseEntity<?> seckillOrder(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody SeckillOrderRequest request) {

        log.info("Seckill order request received, userId: {}, productId: {}, activityId: {}",
                userId, request.getProductId(), request.getSeckillActivityId());

        // 1. 生成幂等性Key
        String idempotencyKey = IdempotencyKeyUtil.generateSeckillKey(
                userId, request.getProductId(), request.getSeckillActivityId());
        log.info("Generated idempotency key: {}", idempotencyKey);

        // 2. 检查幂等性
        if (idempotencyService.isDuplicate(idempotencyKey)) {
            String existingOrderNo = idempotencyService.getOrderNo(idempotencyKey);
            log.warn("Duplicate request detected, idempotencyKey: {}, orderNo: {}",
                    idempotencyKey, existingOrderNo);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 409);
            response.put("message", "重复请求");
            response.put("orderNo", existingOrderNo);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        // 3. 调用库存服务扣减库存
        Long remainingStock = callInventoryDeduct(
                userId,
                request.getProductId(),
                request.getSeckillActivityId(),
                request.getQuantity()
        );

        if (remainingStock == null) {
            log.error("Inventory service call failed");
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("message", "库存服务调用失败");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        if (remainingStock < 0) {
            // 库存不足 (-1) 或用户已参与 (-2)
            String reason = remainingStock == -1 ? "库存不足" : "用户已参与";
            int statusCode = remainingStock == -1 ? 410 : 409;
            log.warn("Stock deduction failed, reason: {}, userId: {}, productId: {}",
                    reason, userId, request.getProductId());

            Map<String, Object> response = new HashMap<>();
            response.put("code", statusCode);
            response.put("message", reason);
            return ResponseEntity.status(statusCode == 410 ? HttpStatus.GONE : HttpStatus.CONFLICT)
                    .body(response);
        }

        log.info("Stock deducted successfully, remainingStock: {}", remainingStock);

        // 4. 生成订单号
        String orderNo = IdGenUtil.generateOrderNo();
        log.info("Generated orderNo: {}", orderNo);

        // 5. 标记幂等性Key为处理中
        boolean marked = idempotencyService.markProcessing(idempotencyKey, orderNo);
        if (!marked) {
            // 并发情况下可能已经被其他请求标记
            log.warn("Failed to mark processing, key already exists: {}", idempotencyKey);
            String existingOrderNo = idempotencyService.getOrderNo(idempotencyKey);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 409);
            response.put("message", "重复请求");
            response.put("orderNo", existingOrderNo);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        // 6. 构建OrderCreateEvent
        OrderCreateEvent event = OrderCreateEvent.builder()
                .idempotencyKey(idempotencyKey)
                .orderNo(orderNo)
                .userId(userId)
                .productId(request.getProductId())
                .seckillActivityId(request.getSeckillActivityId())
                .quantity(request.getQuantity())
                .totalAmount(request.getTotalAmount())
                .createTime(Instant.now().toEpochMilli())
                .build();

        // 7. 发送Kafka消息
        try {
            seckillOrderProducer.sendOrderCreateEvent(event);
            log.info("Order create event sent to Kafka, orderNo: {}", orderNo);
        } catch (Exception e) {
            log.error("Failed to send order create event, orderNo: {}", orderNo, e);
            // 发送失败，清除幂等性标记，允许重试
            // 注意：这里不直接清除，而是依赖24小时过期
        }

        // 8. 返回202状态码和排队中响应
        Map<String, Object> response = new HashMap<>();
        response.put("code", 202);
        response.put("message", "排队中");
        response.put("orderNo", orderNo);
        response.put("status", "PROCESSING");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * 调用库存服务扣减库存
     *
     * @param userId     用户ID
     * @param productId  商品ID
     * @param activityId 活动ID
     * @param quantity   数量
     * @return >=0: 剩余库存; -1: 库存不足; -2: 用户已参与; null: 调用失败
     */
    @SuppressWarnings("unchecked")
    private Long callInventoryDeduct(Long userId, Long productId, Long activityId, Integer quantity) {
        String url = String.format("%s/api/inventory/seckill/deduct/%d/%d/%d/%d",
                inventoryServiceUrl, userId, productId, activityId, quantity);

        log.info("Calling inventory service: {}", url);

        try {
            ResponseEntity<Result> response = restTemplate.postForEntity(url, null, Result.class);

            if (response.getBody() == null) {
                log.error("Inventory service returned empty body");
                return null;
            }

            Result<?> result = response.getBody();
            log.info("Inventory service response: code={}, message={}", result.getCode(), result.getMessage());

            if (result.getCode() == 200) {
                // 扣减成功，返回剩余库存
                Object data = result.getData();
                if (data instanceof Number) {
                    return ((Number) data).longValue();
                }
                return 0L;
            } else if (result.getCode() == 400) {
                // 业务错误
                String message = result.getMessage();
                if ("库存不足".equals(message)) {
                    return -1L;
                } else if ("用户已参与".equals(message)) {
                    return -2L;
                }
            }

            return null;
        } catch (Exception e) {
            log.error("Failed to call inventory service", e);
            return null;
        }
    }
}
