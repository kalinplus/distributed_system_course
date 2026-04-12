package com.course.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.course.ecommerce.dto.InventoryDeductEvent;
import com.course.ecommerce.dto.PaymentCallbackRequest;
import com.course.ecommerce.entity.Order;
import com.course.ecommerce.mapper.OrderMapper;
import com.course.ecommerce.producer.InventoryDeductProducer;
import com.course.ecommerce.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 支付服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderMapper orderMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final InventoryDeductProducer inventoryDeductProducer;

    /**
     * Redis Key前缀
     */
    private static final String PAYMENT_KEY_PREFIX = "payment:";

    /**
     * 过期时间：24小时
     */
    private static final long EXPIRE_HOURS = 24;

    /**
     * 订单状态：待支付
     */
    private static final String STATUS_PENDING = "PENDING";

    /**
     * 订单状态：已支付
     */
    private static final String STATUS_PAID = "PAID";

    /**
     * 订单状态：已取消
     */
    private static final String STATUS_CANCELLED = "CANCELLED";

    /**
     * 支付状态：成功
     */
    private static final String PAYMENT_STATUS_SUCCESS = "SUCCESS";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentCallback(PaymentCallbackRequest request) {
        String paymentNo = request.getPaymentNo();
        String orderNo = request.getOrderNo();
        String paymentStatus = request.getStatus();

        log.info("Processing payment callback, orderNo: {}, paymentNo: {}, status: {}",
                orderNo, paymentNo, paymentStatus);

        // 1. 幂等性检查（Redis: payment:{paymentNo}，24小时过期）
        String paymentKey = PAYMENT_KEY_PREFIX + paymentNo;
        Boolean isNew = stringRedisTemplate.opsForValue()
                .setIfAbsent(paymentKey, "PROCESSING", Duration.ofHours(EXPIRE_HOURS));

        if (Boolean.FALSE.equals(isNew)) {
            log.warn("Duplicate payment callback detected, paymentNo: {}", paymentNo);
            return;
        }

        try {
            // 2. 查询订单
            LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Order::getOrderNo, orderNo);
            Order order = orderMapper.selectOne(queryWrapper);

            if (order == null) {
                log.error("Order not found, orderNo: {}", orderNo);
                throw new RuntimeException("订单不存在: " + orderNo);
            }

            // 3. 状态机校验（仅允许PENDING→PAID/CANCELLED）
            String currentStatus = order.getStatus();
            if (!STATUS_PENDING.equals(currentStatus)) {
                log.warn("Order status is not PENDING, orderNo: {}, currentStatus: {}",
                        orderNo, currentStatus);
                // 幂等性：已经处理过的订单，直接返回成功
                return;
            }

            // 4. 根据支付结果更新订单状态
            String newStatus = PAYMENT_STATUS_SUCCESS.equals(paymentStatus) ? STATUS_PAID : STATUS_CANCELLED;

            LambdaUpdateWrapper<Order> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Order::getOrderNo, orderNo)
                    .eq(Order::getStatus, STATUS_PENDING)  // 乐观锁：确保状态未被其他线程修改
                    .set(Order::getStatus, newStatus)
                    .set(Order::getUpdatedAt, LocalDateTime.now());

            int updated = orderMapper.update(updateWrapper);

            if (updated == 0) {
                log.warn("Order status update failed, possibly concurrent update, orderNo: {}", orderNo);
                throw new RuntimeException("订单状态更新失败，可能已被其他线程处理: " + orderNo);
            }

            // 更新Redis状态为已完成
            stringRedisTemplate.opsForValue().set(paymentKey, "COMPLETED", Duration.ofHours(EXPIRE_HOURS));

            // 支付成功后，发送库存扣减消息（最终一致性）
            if (STATUS_PAID.equals(newStatus)) {
                InventoryDeductEvent event = InventoryDeductEvent.builder()
                        .orderNo(orderNo)
                        .productId(order.getProductId())
                        .quantity(order.getQuantity())
                        .timestamp(System.currentTimeMillis())
                        .build();

                inventoryDeductProducer.sendInventoryDeductEvent(event);
                log.info("Inventory deduct event sent after payment success, orderNo: {}, productId: {}, quantity: {}",
                        orderNo, order.getProductId(), order.getQuantity());
            }

            log.info("Payment callback processed successfully, orderNo: {}, paymentNo: {}, newStatus: {}",
                    orderNo, paymentNo, newStatus);

        } catch (Exception e) {
            // 发生异常时删除Redis Key，允许重试
            stringRedisTemplate.delete(paymentKey);
            log.error("Failed to process payment callback, paymentNo: {}, error: {}", paymentNo, e.getMessage());
            throw e;
        }
    }
}
