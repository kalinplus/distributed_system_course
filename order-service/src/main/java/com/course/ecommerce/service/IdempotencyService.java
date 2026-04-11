package com.course.ecommerce.service;

/**
 * 幂等性服务接口
 * 用于管理秒杀请求的幂等性状态
 */
public interface IdempotencyService {

    /**
     * 检查是否重复请求
     *
     * @param idempotencyKey 幂等性Key
     * @return true: 重复请求; false: 新请求
     */
    boolean isDuplicate(String idempotencyKey);

    /**
     * 标记处理中状态
     *
     * @param idempotencyKey 幂等性Key
     * @param orderNo        订单号
     * @return true: 标记成功; false: 标记失败（已存在）
     */
    boolean markProcessing(String idempotencyKey, String orderNo);

    /**
     * 标记完成状态
     *
     * @param idempotencyKey 幂等性Key
     */
    void markCompleted(String idempotencyKey);

    /**
     * 获取关联的订单号
     *
     * @param idempotencyKey 幂等性Key
     * @return 订单号，不存在返回null
     */
    String getOrderNo(String idempotencyKey);
}
