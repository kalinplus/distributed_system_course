package com.course.ecommerce.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.ecommerce.entity.Order;
import com.course.ecommerce.mapper.OrderMapper;
import com.course.ecommerce.service.OrderCancelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时定时任务
 * 定期检查并取消超时未支付的订单
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final OrderMapper orderMapper;
    private final OrderCancelService orderCancelService;

    private static final long TIMEOUT_MINUTES = 15;

    /**
     * 每5分钟执行一次超时订单检查
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void cancelTimeoutOrders() {
        log.info("Starting scheduled task to cancel timeout orders");

        // 查询15分钟前创建的PENDING订单
        LocalDateTime timeoutBefore = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);

        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getStatus, "PENDING")
                .lt(Order::getCreatedAt, timeoutBefore);

        List<Order> timeoutOrders = orderMapper.selectList(queryWrapper);

        if (timeoutOrders.isEmpty()) {
            log.info("No timeout orders found");
            return;
        }

        log.info("Found {} timeout orders to cancel", timeoutOrders.size());

        int successCount = 0;
        int failCount = 0;

        for (Order order : timeoutOrders) {
            try {
                boolean cancelled = orderCancelService.cancelTimeoutOrder(
                        order.getOrderNo(),
                        order.getUserId(),
                        order.getProductId(),
                        order.getSeckillActivityId(),
                        order.getQuantity()
                );

                if (cancelled) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                log.error("Failed to cancel order in scheduler, orderNo: {}, error: {}",
                        order.getOrderNo(), e.getMessage());
                failCount++;
                // 捕获异常继续处理其他订单
            }
        }

        log.info("Timeout order cancellation completed. Success: {}, Failed: {}", successCount, failCount);
    }
}
