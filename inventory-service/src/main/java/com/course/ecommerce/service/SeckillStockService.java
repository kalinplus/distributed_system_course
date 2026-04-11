package com.course.ecommerce.service;

/**
 * 秒杀库存服务接口
 * 基于Redis Lua脚本实现原子扣减
 */
public interface SeckillStockService {

    /**
     * 预热库存
     * @param productId 商品ID
     * @param stock 库存数量
     * @param expireSeconds 过期时间(秒)
     */
    void warmupStock(Long productId, Long stock, Integer expireSeconds);

    /**
     * 扣减库存
     * @param userId 用户ID
     * @param productId 商品ID
     * @param activityId 活动ID
     * @param quantity 扣减数量
     * @return >=0: 成功，返回剩余库存; -1: 库存不足; -2: 用户已参与; -3: 库存未初始化
     */
    Long deductStock(Long userId, Long productId, Long activityId, Integer quantity);

    /**
     * 回滚库存
     * @param productId 商品ID
     * @param quantity 回滚数量
     */
    void rollbackStock(Long productId, Integer quantity);

    /**
     * 查询库存
     * @param productId 商品ID
     * @return 当前库存，未初始化返回null
     */
    Long getStock(Long productId);

    /**
     * 清除用户去重标记（用于测试或管理）
     * @param userId 用户ID
     * @param productId 商品ID
     * @param activityId 活动ID
     */
    void clearDedupKey(Long userId, Long productId, Long activityId);
}
