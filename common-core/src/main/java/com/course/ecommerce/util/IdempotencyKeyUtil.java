package com.course.ecommerce.util;

/**
 * 幂等性Key生成工具
 * 用于生成秒杀场景的幂等性Key和去重Key
 */
public class IdempotencyKeyUtil {

    /**
     * Key前缀：秒杀幂等性Key
     */
    private static final String SECKILL_KEY_PREFIX = "seckill";

    /**
     * Key前缀：秒杀去重Key
     */
    private static final String DEDUP_KEY_PREFIX = "seckill:dup";

    /**
     * Key分隔符
     */
    private static final String KEY_SEPARATOR = ":";

    /**
     * 秒杀Key信息记录
     */
    public record SeckillKeyInfo(Long userId, Long productId, Long activityId) {
    }

    /**
     * 生成秒杀幂等性Key
     * 格式: seckill:{userId}:{productId}:{activityId}
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @param activityId 活动ID
     * @return 幂等性Key
     */
    public static String generateSeckillKey(Long userId, Long productId, Long activityId) {
        return SECKILL_KEY_PREFIX + KEY_SEPARATOR + userId + KEY_SEPARATOR + productId + KEY_SEPARATOR + activityId;
    }

    /**
     * 生成秒杀去重Key
     * 格式: seckill:dup:{userId}:{productId}:{activityId}
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @param activityId 活动ID
     * @return 去重Key
     */
    public static String generateDedupKey(Long userId, Long productId, Long activityId) {
        return DEDUP_KEY_PREFIX + KEY_SEPARATOR + userId + KEY_SEPARATOR + productId + KEY_SEPARATOR + activityId;
    }

    /**
     * 从Key解析秒杀信息
     * 支持解析 seckill:{userId}:{productId}:{activityId} 格式的Key
     *
     * @param key 幂等性Key
     * @return SeckillKeyInfo 解析后的信息，解析失败返回null
     */
    public static SeckillKeyInfo parseKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        String[] parts = key.split(KEY_SEPARATOR);
        // 期望格式: seckill:userId:productId:activityId 或 seckill:dup:userId:productId:activityId
        if (parts.length < 4) {
            return null;
        }

        try {
            int offset = SECKILL_KEY_PREFIX.equals(parts[0]) ? 1 : 0;
            if (parts.length == 5 && DEDUP_KEY_PREFIX.equals(parts[0] + KEY_SEPARATOR + parts[1])) {
                // seckill:dup:userId:productId:activityId
                Long userId = Long.parseLong(parts[2]);
                Long productId = Long.parseLong(parts[3]);
                Long activityId = Long.parseLong(parts[4]);
                return new SeckillKeyInfo(userId, productId, activityId);
            } else if (parts.length == 4) {
                // seckill:userId:productId:activityId
                Long userId = Long.parseLong(parts[1]);
                Long productId = Long.parseLong(parts[2]);
                Long activityId = Long.parseLong(parts[3]);
                return new SeckillKeyInfo(userId, productId, activityId);
            }
        } catch (NumberFormatException e) {
            return null;
        }

        return null;
    }
}
