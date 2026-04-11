package com.course.ecommerce.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 雪花算法ID生成器
 * 结构: 1bit符号位 + 41bit时间戳 + 10bit工作节点 + 12bit序列号
 * 起始时间戳: 2024-01-01 00:00:00 (1704067200000L)
 */
public class SnowflakeIdGenerator {

    /**
     * 起始时间戳 (2024-01-01 00:00:00 UTC)
     */
    private static final long START_TIMESTAMP = 1704067200000L;

    /**
     * 工作节点ID占用的位数
     */
    private static final long WORKER_ID_BITS = 10L;

    /**
     * 序列号占用的位数
     */
    private static final long SEQUENCE_BITS = 12L;

    /**
     * 工作节点ID的最大值 (2^10 - 1 = 1023)
     */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /**
     * 序列号的最大值 (2^12 - 1 = 4095)
     */
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    /**
     * 工作节点ID左移的位数
     */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /**
     * 时间戳左移的位数
     */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /**
     * 工作节点ID
     */
    private final long workerId;

    /**
     * 序列号
     */
    private long sequence = 0L;

    /**
     * 上次生成ID的时间戳
     */
    private long lastTimestamp = -1L;

    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    /**
     * 构造函数
     *
     * @param workerId 工作节点ID (0-1023)
     */
    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    String.format("Worker ID must be between 0 and %d", MAX_WORKER_ID));
        }
        this.workerId = workerId;
    }

    /**
     * 生成下一个唯一ID (线程安全)
     *
     * @return 唯一ID
     */
    public synchronized long nextId() {
        long currentTimestamp = getCurrentTimestamp();

        // 检测时钟回拨
        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException(
                    String.format("Clock moved backwards. Refusing to generate id for %d milliseconds",
                            lastTimestamp - currentTimestamp));
        }

        // 同一毫秒内生成多个ID
        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // 序列号溢出，等待下一毫秒
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(currentTimestamp);
            }
        } else {
            // 不同毫秒，序列号重置
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        // 组合ID: (时间戳 - 起始时间戳) << 22 | 工作节点ID << 12 | 序列号
        return ((currentTimestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 等待下一毫秒
     *
     * @param lastTimestamp 上次时间戳
     * @return 当前时间戳
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = getCurrentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimestamp();
        }
        return timestamp;
    }

    /**
     * 获取当前时间戳 (毫秒)
     *
     * @return 当前时间戳
     */
    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 从ID中提取时间戳
     *
     * @param id 雪花算法生成的ID
     * @return 时间戳 (毫秒)
     */
    public static long extractTimestamp(long id) {
        return (id >> TIMESTAMP_SHIFT) + START_TIMESTAMP;
    }

    /**
     * 从ID中提取工作节点ID
     *
     * @param id 雪花算法生成的ID
     * @return 工作节点ID
     */
    public static long extractWorkerId(long id) {
        return (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    /**
     * 从ID中提取序列号
     *
     * @param id 雪花算法生成的ID
     * @return 序列号
     */
    public static long extractSequence(long id) {
        return id & MAX_SEQUENCE;
    }

    /**
     * 将时间戳格式化为可读字符串
     *
     * @param timestamp 时间戳 (毫秒)
     * @return 格式化后的时间字符串
     */
    public static String formatTimestamp(long timestamp) {
        return DATE_FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }

    /**
     * 获取起始时间戳
     *
     * @return 起始时间戳
     */
    public static long getStartTimestamp() {
        return START_TIMESTAMP;
    }

    /**
     * 获取最大工作节点ID
     *
     * @return 最大工作节点ID
     */
    public static long getMaxWorkerId() {
        return MAX_WORKER_ID;
    }

    /**
     * 获取工作节点ID
     *
     * @return 工作节点ID
     */
    public long getWorkerId() {
        return workerId;
    }
}
