package com.course.ecommerce.util;

/**
 * ID生成工具类
 * 提供静态方法用于生成订单号、订单ID等
 */
public class IdGenUtil {

    /**
     * 默认工作节点ID
     */
    private static final long DEFAULT_WORKER_ID = 1L;

    /**
     * 雪花算法ID生成器实例 (单例)
     */
    private static final SnowflakeIdGenerator ID_GENERATOR = new SnowflakeIdGenerator(DEFAULT_WORKER_ID);

    /**
     * 私有构造函数，防止实例化
     */
    private IdGenUtil() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 生成订单号 (String类型)
     * 格式: 纯数字字符串，基于雪花算法
     *
     * @return 订单号
     */
    public static String generateOrderNo() {
        return String.valueOf(ID_GENERATOR.nextId());
    }

    /**
     * 生成订单ID (Long类型)
     *
     * @return 订单ID
     */
    public static Long generateOrderId() {
        return ID_GENERATOR.nextId();
    }

    /**
     * 生成唯一ID (Long类型)
     *
     * @return 唯一ID
     */
    public static Long generateId() {
        return ID_GENERATOR.nextId();
    }

    /**
     * 生成唯一ID (String类型)
     *
     * @return 唯一ID字符串
     */
    public static String generateIdStr() {
        return String.valueOf(ID_GENERATOR.nextId());
    }

    /**
     * 从订单号中提取时间戳
     *
     * @param orderNo 订单号
     * @return 时间戳 (毫秒)
     */
    public static long extractTimestampFromOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isEmpty()) {
            throw new IllegalArgumentException("Order number cannot be null or empty");
        }
        try {
            long id = Long.parseLong(orderNo);
            return SnowflakeIdGenerator.extractTimestamp(id);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid order number format: " + orderNo);
        }
    }

    /**
     * 从订单ID中提取时间戳
     *
     * @param orderId 订单ID
     * @return 时间戳 (毫秒)
     */
    public static long extractTimestampFromOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        return SnowflakeIdGenerator.extractTimestamp(orderId);
    }

    /**
     * 从订单号中提取工作节点ID
     *
     * @param orderNo 订单号
     * @return 工作节点ID
     */
    public static long extractWorkerIdFromOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isEmpty()) {
            throw new IllegalArgumentException("Order number cannot be null or empty");
        }
        try {
            long id = Long.parseLong(orderNo);
            return SnowflakeIdGenerator.extractWorkerId(id);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid order number format: " + orderNo);
        }
    }

    /**
     * 从订单ID中提取工作节点ID
     *
     * @param orderId 订单ID
     * @return 工作节点ID
     */
    public static long extractWorkerIdFromOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        return SnowflakeIdGenerator.extractWorkerId(orderId);
    }
}
