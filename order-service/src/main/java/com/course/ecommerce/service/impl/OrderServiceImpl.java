package com.course.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.ecommerce.common.exception.BusinessException;
import com.course.ecommerce.entity.Order;
import com.course.ecommerce.event.OrderCreateEvent;
import com.course.ecommerce.mapper.OrderMapper;
import com.course.ecommerce.service.IdempotencyService;
import com.course.ecommerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final IdempotencyService idempotencyService;

    @Override
    public Order getOrderById(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrderFromSeckill(OrderCreateEvent event) {
        // 1. 检查订单是否已存在（通过orderNo查DB）
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getOrderNo, event.getOrderNo());
        Order existingOrder = orderMapper.selectOne(queryWrapper);

        if (existingOrder != null) {
            log.warn("Order already exists, orderNo: {}", event.getOrderNo());
            return;
        }

        // 2. 创建订单实体
        Order order = new Order();
        order.setOrderNo(event.getOrderNo());
        order.setUserId(event.getUserId());
        order.setProductId(event.getProductId());
        order.setQuantity(event.getQuantity());
        order.setTotalAmount(event.getTotalAmount());

        // 3. 设置状态为"PENDING"
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // 4. 保存到数据库
        orderMapper.insert(order);
        log.info("Order created successfully, orderNo: {}, id: {}", event.getOrderNo(), order.getId());

        // 5. 标记幂等性Key为已完成
        idempotencyService.markCompleted(event.getIdempotencyKey());
        log.info("Marked idempotency key as completed: {}", event.getIdempotencyKey());
    }
}
