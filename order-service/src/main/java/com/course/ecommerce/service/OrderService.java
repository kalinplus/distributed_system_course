package com.course.ecommerce.service;

import com.course.ecommerce.entity.Order;

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
}
