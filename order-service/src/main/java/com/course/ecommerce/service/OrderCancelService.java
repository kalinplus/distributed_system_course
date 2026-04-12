package com.course.ecommerce.service;

/**
 * 订单取消服务接口
 * 用于处理订单超时自动取消逻辑
 */
public interface OrderCancelService {

    /**
     * 取消超时订单
     * 包含幂等性检查、状态更新、库存回滚等逻辑
     *
     * @param orderNo 订单号
     * @param userId 用户ID
     * @param productId 商品ID
     * @param seckillActivityId 秒杀活动ID
     * @param quantity 数量
     * @return true: 取消成功; false: 取消失败或已处理
     */
    boolean cancelTimeoutOrder(String orderNo, Long userId, Long productId,
                               Long seckillActivityId, Integer quantity);
}
