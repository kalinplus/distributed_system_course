package com.course.ecommerce.service;

import com.course.ecommerce.event.OrderCreateEvent;

/**
 * 补偿服务接口
 * 用于处理订单创建失败时的补偿逻辑
 */
public interface CompensationService {

    /**
     * 补偿订单创建失败
     * 调用库存服务回滚库存，保持幂等性Key让用户保持"已参与"状态
     *
     * @param event 订单创建事件
     */
    void compensateOrderCreation(OrderCreateEvent event);
}
