package com.course.ecommerce.service;

import com.course.ecommerce.entity.Order;
import com.course.ecommerce.event.OrderCreateEvent;

/**
 * 订单服务接口
 */
public interface OrderService {

    /**
     * 根据 ID 查询订单
     * @param id 订单 ID
     * @return 订单信息
     */
    Order getOrderById(Long id);

    /**
     * 从秒杀事件创建订单
     * @param event 订单创建事件
     */
    void createOrderFromSeckill(OrderCreateEvent event);
}
