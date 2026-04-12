# 秒杀下单系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现完整的秒杀下单系统，包含Redis缓存库存原子扣减、Kafka异步削峰、雪花算法订单ID、幂等性保证、最终一致性。

**Architecture:** 漏斗型流量削峰架构 - API限流 → Redis Lua原子扣减 → Kafka消息队列 → 异步创建订单 → 补偿回滚机制。inventory-service负责库存管理，order-service负责订单流程，common-core提供共享组件。

**Tech Stack:** Spring Boot 3.2.x, Kafka 3.6, Redis 7.2, Snowflake ID, Lua脚本, MyBatis-Plus

---

## FILE MAP

```
common-core/src/main/java/com/course/ecommerce/
├── util/
│   ├── SnowflakeIdGenerator.java       [CREATE] 雪花算法实现
│   └── IdGenUtil.java                  [CREATE] ID生成工具类
├── event/
│   └── OrderCreateEvent.java           [CREATE] 订单创建事件DTO
└── util/
    └── IdempotencyKeyUtil.java         [CREATE] 幂等性Key工具

inventory-service/src/main/java/com/course/ecommerce/
├── service/
│   ├── SeckillStockService.java        [CREATE] 秒杀库存服务接口
│   └── impl/
│       └── SeckillStockServiceImpl.java [CREATE] 实现类
├── controller/
│   ├── SeckillInventoryController.java  [CREATE] 秒杀库存API
│   └── InventoryInternalController.java [CREATE] 内部库存回滚接口
└── resources/
    └── scripts/
        └── stock_deduct.lua            [CREATE] 原子扣减Lua脚本

order-service/src/main/java/com/course/ecommerce/
├── config/
│   ├── KafkaProducerConfig.java        [CREATE] Kafka生产者配置
│   └── KafkaConsumerConfig.java        [CREATE] Kafka消费者配置
├── producer/
│   └── SeckillOrderProducer.java       [CREATE] 秒杀订单生产者
├── consumer/
│   └── SeckillOrderConsumer.java       [CREATE] 秒杀订单消费者
├── service/
│   ├── IdempotencyService.java         [CREATE] 幂等性服务接口
│   ├── CompensationService.java        [CREATE] 补偿服务接口
│   ├── OrderService.java               [MODIFY] 添加createOrderFromSeckill
│   └── impl/
│       ├── IdempotencyServiceImpl.java [CREATE] 幂等性实现
│       ├── CompensationServiceImpl.java [CREATE] 补偿实现
│       └── OrderServiceImpl.java       [MODIFY] 实现秒杀订单创建
├── controller/
│   └── SeckillController.java          [CREATE] 秒杀API
└── resources/
    └── application-prod.yml            [MODIFY] 添加Kafka配置

sql/
└── init.sql                            [MODIFY] t_order添加唯一索引
docker-compose.yml                       [MODIFY] 添加kafka、zookeeper
jmeter/
└── seckill-test.jmx                    [CREATE] 秒杀压测脚本
```

---

## PART A: 雪花算法ID生成器

### Task A1: Create SnowflakeIdGenerator

**Files:**
- Create: `common-core/src/main/java/com/course/ecommerce/util/SnowflakeIdGenerator.java`

- [ ] **Step 1: Create the SnowflakeIdGenerator class**

```java
package com.course.ecommerce.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 雪花算法ID生成器
 * 结构: 1bit符号位 + 41bit时间戳 + 10bit工作节点 + 12bit序列号
 */
public class SnowflakeIdGenerator {

    // 起始时间戳 (2024-01-01 00:00:00)
    private static final long START_TIMESTAMP = 1704067200000L;

    // 各部分的位数
    private static final long WORKER_ID_BITS = 10L;      // 工作节点ID占10位
    private static final long SEQUENCE_BITS = 12L;       // 序列号占12位

    // 各部分的最大值
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);  // 1023
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);    // 4095

    // 各部分的偏移量
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;                          // 12
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;        // 22

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /**
     * @param workerId 工作节点ID (0-1023)
     */
    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("Worker ID must be between 0 and " + MAX_WORKER_ID);
        }
        this.workerId = workerId;
    }

    /**
     * 生成下一个ID
     */
    public synchronized long nextId() {
        long timestamp = currentTimestamp();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate ID");
        }

        if (timestamp == lastTimestamp) {
            // 同一毫秒内，序列号递增
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 序列号溢出，等待下一毫秒
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，序列号重置
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 组合ID
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 从ID中提取时间戳
     */
    public static long extractTimestamp(long id) {
        return (id >> TIMESTAMP_SHIFT) + START_TIMESTAMP;
    }

    /**
     * 从ID中提取工作节点ID
     */
    public static long extractWorkerId(long id) {
        return (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimestamp();
        }
        return timestamp;
    }

    private long currentTimestamp() {
        return System.currentTimeMillis();
    }
}
```

- [ ] **Step 2: Create IdGenUtil wrapper**

Create: `common-core/src/main/java/com/course/ecommerce/util/IdGenUtil.java`

```java
package com.course.ecommerce.util;

/**
 * ID生成工具类
 * 提供订单号、幂等性Key等生成方法
 */
public class IdGenUtil {

    private static final SnowflakeIdGenerator ORDER_ID_GENERATOR = new SnowflakeIdGenerator(1);

    /**
     * 生成订单号（纯数字）
     */
    public static String generateOrderNo() {
        return String.valueOf(ORDER_ID_GENERATOR.nextId());
    }

    /**
     * 生成Long类型订单ID
     */
    public static Long generateOrderId() {
        return ORDER_ID_GENERATOR.nextId();
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add common-core/src/main/java/com/course/ecommerce/util/
git commit -m "feat: add Snowflake ID generator for distributed order ID"
```

---

## PART B: Kafka事件DTO与幂等性工具

### Task B2: Create OrderCreateEvent

**Files:**
- Create: `common-core/src/main/java/com/course/ecommerce/event/OrderCreateEvent.java`

- [ ] **Step 1: Create OrderCreateEvent DTO**

```java
package com.course.ecommerce.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单创建事件
 * 用于Kafka消息传递
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 幂等性Key (用于防重)
     */
    private String idempotencyKey;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 秒杀活动ID
     */
    private Long seckillActivityId;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 订单金额
     */
    private BigDecimal totalAmount;

    /**
     * 创建时间戳
     */
    private Long createTime;
}
```

- [ ] **Step 2: Create IdempotencyKeyUtil**

Create: `common-core/src/main/java/com/course/ecommerce/util/IdempotencyKeyUtil.java`

```java
package com.course.ecommerce.util;

/**
 * 幂等性Key生成工具
 */
public class IdempotencyKeyUtil {

    private static final String SECKILL_PREFIX = "seckill";

    /**
     * 生成秒杀幂等性Key
     * 格式: seckill:{userId}:{productId}:{activityId}
     */
    public static String generateSeckillKey(Long userId, Long productId, Long activityId) {
        return String.format("%s:%d:%d:%d", SECKILL_PREFIX, userId, productId, activityId);
    }

    /**
     * 生成Redis防重Key
     * 格式: seckill:dup:{userId}:{productId}:{activityId}
     */
    public static String generateDedupKey(Long userId, Long productId, Long activityId) {
        return String.format("seckill:dup:%d:%d:%d", userId, productId, activityId);
    }

    /**
     * 从幂等性Key解析信息
     */
    public static SeckillKeyInfo parseKey(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid seckill key format");
        }
        return new SeckillKeyInfo(
                Long.parseLong(parts[1]),
                Long.parseLong(parts[2]),
                Long.parseLong(parts[3])
        );
    }

    public record SeckillKeyInfo(Long userId, Long productId, Long activityId) {}
}
```

- [ ] **Step 3: Add Lombok dependency check**

Check `common-core/pom.xml` has Lombok:
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

- [ ] **Step 4: Commit**

```bash
git add common-core/src/main/java/com/course/ecommerce/event/
git add common-core/src/main/java/com/course/ecommerce/util/IdempotencyKeyUtil.java
git commit -m "feat: add OrderCreateEvent and IdempotencyKeyUtil for seckill"
```

---

## PART C: Redis库存扣减服务

### Task C1: Create Lua Script for Atomic Stock Deduct

**Files:**
- Create: `inventory-service/src/main/resources/scripts/stock_deduct.lua`

- [ ] **Step 1: Create stock_deduct.lua**

```lua
-- 秒杀库存原子扣减脚本
-- KEYS[1]: 库存Key (seckill:stock:{productId})
-- KEYS[2]: 用户已购标记Key (seckill:dup:{userId}:{productId}:{activityId})
-- ARGV[1]: 扣减数量
-- ARGV[2]: 过期时间(秒)

local stockKey = KEYS[1]
local dedupKey = KEYS[2]
local deductCount = tonumber(ARGV[1])
local expireSeconds = tonumber(ARGV[2])

-- 1. 检查用户是否已参与
if redis.call('EXISTS', dedupKey) == 1 then
    return -2  -- 已参与，返回-2
end

-- 2. 获取当前库存
local currentStock = redis.call('GET', stockKey)
if currentStock == false then
    return -3  -- 库存未初始化，返回-3
end

currentStock = tonumber(currentStock)

-- 3. 检查库存是否充足
if currentStock < deductCount then
    return -1  -- 库存不足，返回-1
end

-- 4. 扣减库存
local newStock = redis.call('DECRBY', stockKey, deductCount)

-- 5. 标记用户已参与
redis.call('SET', dedupKey, '1', 'EX', expireSeconds)

-- 6. 返回剩余库存
return newStock
```

- [ ] **Step 2: Create SeckillStockService interface**

Create: `inventory-service/src/main/java/com/course/ecommerce/service/SeckillStockService.java`

```java
package com.course.ecommerce.service;

/**
 * 秒杀库存服务
 */
public interface SeckillStockService {

    /**
     * 库存预热 - 从MySQL加载库存到Redis
     * @param productId 商品ID
     * @param stock 库存数量
     * @param expireSeconds 过期时间(秒)
     */
    void warmupStock(Long productId, Integer stock, Integer expireSeconds);

    /**
     * 原子扣减库存
     * @param userId 用户ID
     * @param productId 商品ID
     * @param activityId 活动ID
     * @param quantity 扣减数量
     * @return 扣减结果: >=0剩余库存, -1库存不足, -2已参与, -3库存未初始化
     */
    Long deductStock(Long userId, Long productId, Long activityId, Integer quantity);

    /**
     * 回滚库存（订单创建失败时调用）
     * @param productId 商品ID
     * @param quantity 回滚数量
     */
    void rollbackStock(Long productId, Integer quantity);

    /**
     * 查询剩余库存
     * @param productId 商品ID
     * @return 剩余库存
     */
    Long getStock(Long productId);

    /**
     * 清除用户参与标记（用于测试）
     * @param userId 用户ID
     * @param productId 商品ID
     * @param activityId 活动ID
     */
    void clearDedupKey(Long userId, Long productId, Long activityId);
}
```

- [ ] **Step 3: Create SeckillStockServiceImpl**

Create: `inventory-service/src/main/java/com/course/ecommerce/service/impl/SeckillStockServiceImpl.java`

```java
package com.course.ecommerce.service.impl;

import com.course.ecommerce.service.SeckillStockService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillStockServiceImpl implements SeckillStockService {

    private static final Logger log = LoggerFactory.getLogger(SeckillStockServiceImpl.class);
    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String DEDUP_KEY_PREFIX = "seckill:dup:";
    private static final int DEFAULT_EXPIRE_SECONDS = 3600; // 1小时

    @Autowired
    private StringRedisTemplate redisTemplate;

    private DefaultRedisScript<Long> stockDeductScript;

    @PostConstruct
    public void init() {
        stockDeductScript = new DefaultRedisScript<>();
        stockDeductScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("scripts/stock_deduct.lua")));
        stockDeductScript.setResultType(Long.class);
    }

    @Override
    public void warmupStock(Long productId, Integer stock, Integer expireSeconds) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
        redisTemplate.expire(stockKey, expireSeconds, TimeUnit.SECONDS);
        log.info("Stock warmup completed: productId={}, stock={}", productId, stock);
    }

    @Override
    public Long deductStock(Long userId, Long productId, Long activityId, Integer quantity) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        String dedupKey = DEDUP_KEY_PREFIX + userId + ":" + productId + ":" + activityId;

        try {
            Long result = redisTemplate.execute(stockDeductScript,
                    Collections.singletonList(stockKey),
                    String.valueOf(quantity),
                    String.valueOf(DEFAULT_EXPIRE_SECONDS));

            if (result == null) {
                log.error("Lua script execution returned null");
                return -3L;
            }

            log.info("Stock deduct result: productId={}, result={}", productId, result);
            return result;
        } catch (Exception e) {
            log.error("Stock deduct failed: productId={}, error={}", productId, e.getMessage());
            return -3L;
        }
    }

    @Override
    public void rollbackStock(Long productId, Integer quantity) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        try {
            redisTemplate.opsForValue().increment(stockKey, quantity);
            log.info("Stock rollback completed: productId={}, quantity={}", productId, quantity);
        } catch (Exception e) {
            log.error("Stock rollback failed: productId={}, error={}", productId, e.getMessage());
        }
    }

    @Override
    public Long getStock(Long productId) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        String stock = redisTemplate.opsForValue().get(stockKey);
        return stock == null ? 0L : Long.parseLong(stock);
    }

    @Override
    public void clearDedupKey(Long userId, Long productId, Long activityId) {
        String dedupKey = DEDUP_KEY_PREFIX + userId + ":" + productId + ":" + activityId;
        redisTemplate.delete(dedupKey);
    }
}
```

- [ ] **Step 4: Create SeckillInventoryController**

Create: `inventory-service/src/main/java/com/course/ecommerce/controller/SeckillInventoryController.java`

```java
package com.course.ecommerce.controller;

import com.course.ecommerce.common.Result;
import com.course.ecommerce.service.SeckillStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/seckill")
public class SeckillInventoryController {

    private static final Logger log = LoggerFactory.getLogger(SeckillInventoryController.class);

    @Autowired
    private SeckillStockService seckillStockService;

    /**
     * 库存预热
     */
    @PostMapping("/warmup/{productId}/{stock}")
    public Result<?> warmupStock(
            @PathVariable Long productId,
            @PathVariable Integer stock,
            @RequestParam(defaultValue = "3600") Integer expireSeconds) {
        log.info("Warmup stock request: productId={}, stock={}", productId, stock);
        seckillStockService.warmupStock(productId, stock, expireSeconds);
        return Result.success("库存预热成功");
    }

    /**
     * 扣减库存（内部调用，实际通过Lua脚本在秒杀时扣减）
     */
    @PostMapping("/deduct/{userId}/{productId}/{activityId}/{quantity}")
    public Result<Long> deductStock(
            @PathVariable Long userId,
            @PathVariable Long productId,
            @PathVariable Long activityId,
            @PathVariable Integer quantity) {
        Long result = seckillStockService.deductStock(userId, productId, activityId, quantity);
        if (result >= 0) {
            return Result.success(result);
        } else if (result == -1) {
            return Result.error(410, "库存不足");
        } else if (result == -2) {
            return Result.error(409, "已参与该商品秒杀");
        } else {
            return Result.error(500, "库存扣减失败");
        }
    }

    /**
     * 查询库存
     */
    @GetMapping("/{productId}")
    public Result<Long> getStock(@PathVariable Long productId) {
        Long stock = seckillStockService.getStock(productId);
        return Result.success(stock);
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add inventory-service/src/main/java/com/course/ecommerce/service/
git add inventory-service/src/main/java/com/course/ecommerce/controller/SeckillInventoryController.java
git add inventory-service/src/main/resources/scripts/
git commit -m "feat: add seckill stock service with Lua atomic deduct"
```

---

## PART D: Kafka配置与生产者

### Task D1: Add Kafka dependencies and configuration

**Files:**
- Modify: `order-service/pom.xml`
- Create: `order-service/src/main/java/com/course/ecommerce/config/KafkaProducerConfig.java`

- [ ] **Step 1: Add Kafka dependency to order-service/pom.xml**

Add to `order-service/pom.xml` dependencies section:

```xml
<!-- Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

- [ ] **Step 2: Create KafkaProducerConfig**

Create: `order-service/src/main/java/com/course/ecommerce/config/KafkaProducerConfig.java`

```java
package com.course.ecommerce.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "1");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

- [ ] **Step 3: Create SeckillOrderProducer**

Create: `order-service/src/main/java/com/course/ecommerce/producer/SeckillOrderProducer.java`

```java
package com.course.ecommerce.producer;

import com.course.ecommerce.event.OrderCreateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class SeckillOrderProducer {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderProducer.class);

    @Value("${kafka.topic.seckill-order:seckill-order}")
    private String seckillOrderTopic;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 发送秒杀订单创建消息
     */
    public CompletableFuture<SendResult<String, Object>> sendOrderCreateEvent(OrderCreateEvent event) {
        String key = event.getIdempotencyKey();

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                seckillOrderTopic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("OrderCreateEvent sent successfully: orderNo={}, partition={}",
                        event.getOrderNo(), result.getRecordMetadata().partition());
            } else {
                log.error("OrderCreateEvent send failed: orderNo={}, error={}",
                        event.getOrderNo(), ex.getMessage());
            }
        });

        return future;
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add order-service/pom.xml
git add order-service/src/main/java/com/course/ecommerce/config/KafkaProducerConfig.java
git add order-service/src/main/java/com/course/ecommerce/producer/
git commit -m "feat: add Kafka producer config and SeckillOrderProducer"
```

---

## PART E: 秒杀API与幂等性服务

### Task E1: Create SeckillController and IdempotencyService

**Files:**
- Create: `order-service/src/main/java/com/course/ecommerce/controller/SeckillController.java`
- Create: `order-service/src/main/java/com/course/ecommerce/service/IdempotencyService.java`
- Create: `order-service/src/main/java/com/course/ecommerce/service/impl/IdempotencyServiceImpl.java`

- [ ] **Step 1: Create SeckillController**

```java
package com.course.ecommerce.controller;

import com.course.ecommerce.common.Result;
import com.course.ecommerce.event.OrderCreateEvent;
import com.course.ecommerce.producer.SeckillOrderProducer;
import com.course.ecommerce.service.IdempotencyService;
import com.course.ecommerce.util.IdGenUtil;
import com.course.ecommerce.util.IdempotencyKeyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class SeckillController {

    private static final Logger log = LoggerFactory.getLogger(SeckillController.class);

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private SeckillOrderProducer seckillOrderProducer;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${inventory.service.url:http://inventory-service:8084}")
    private String inventoryServiceUrl;

    /**
     * 秒杀下单
     */
    @PostMapping("/seckill")
    public Result<?> createSeckillOrder(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody SeckillOrderRequest request) {

        log.info("Seckill order request: userId={}, productId={}, quantity={}",
                userId, request.getProductId(), request.getQuantity());

        // 1. 生成幂等性Key
        String idempotencyKey = IdempotencyKeyUtil.generateSeckillKey(
                userId, request.getProductId(), request.getSeckillActivityId());

        // 2. 检查幂等性（快速失败）
        if (idempotencyService.isDuplicate(idempotencyKey)) {
            log.warn("Duplicate seckill request: userId={}, productId={}",
                    userId, request.getProductId());
            return Result.error(409, "您已参与该商品秒杀");
        }

        // 3. 调用库存服务扣减库存
        Long stockResult = deductStock(userId, request.getProductId(),
                request.getSeckillActivityId(), request.getQuantity());

        if (stockResult == null || stockResult < 0) {
            if (stockResult != null && stockResult == -2) {
                return Result.error(409, "您已参与该商品秒杀");
            }
            return Result.error(410, "库存不足");
        }

        // 4. 生成订单号
        String orderNo = IdGenUtil.generateOrderNo();

        // 5. 构建订单创建事件
        OrderCreateEvent event = OrderCreateEvent.builder()
                .idempotencyKey(idempotencyKey)
                .orderNo(orderNo)
                .userId(userId)
                .productId(request.getProductId())
                .seckillActivityId(request.getSeckillActivityId())
                .quantity(request.getQuantity())
                .totalAmount(request.getTotalAmount())
                .createTime(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();

        // 6. 发送Kafka消息
        seckillOrderProducer.sendOrderCreateEvent(event);

        // 7. 标记幂等性Key
        idempotencyService.markProcessing(idempotencyKey, orderNo);

        // 8. 返回排队中响应
        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderNo);
        result.put("status", "PROCESSING");
        result.put("message", "订单处理中，请稍后查询结果");

        log.info("Seckill order queued: orderNo={}", orderNo);
        return Result.success(202, "排队中", result);
    }

    private Long deductStock(Long userId, Long productId, Long activityId, Integer quantity) {
        try {
            String url = String.format("%s/api/inventory/seckill/deduct/%d/%d/%d/%d",
                    inventoryServiceUrl, userId, productId, activityId, quantity);
            Result<Long> result = restTemplate.postForObject(url, null, Result.class);
            if (result != null && result.getCode() == 200) {
                return result.getData();
            }
            return -1L;
        } catch (Exception e) {
            log.error("Deduct stock failed: {}", e.getMessage());
            return -1L;
        }
    }

    // Inner request class
    @lombok.Data
    public static class SeckillOrderRequest {
        private Long productId;
        private Long seckillActivityId;
        private Integer quantity;
        private BigDecimal totalAmount;
    }
}
```

- [ ] **Step 2: Create IdempotencyService interface**

```java
package com.course.ecommerce.service;

/**
 * 幂等性服务
 */
public interface IdempotencyService {

    /**
     * 检查是否重复请求
     */
    boolean isDuplicate(String idempotencyKey);

    /**
     * 标记为处理中
     */
    void markProcessing(String idempotencyKey, String orderNo);

    /**
     * 标记为已完成
     */
    void markCompleted(String idempotencyKey);

    /**
     * 获取关联的订单号
     */
    String getOrderNo(String idempotencyKey);
}
```

- [ ] **Step 3: Create IdempotencyServiceImpl**

```java
package com.course.ecommerce.service.impl;

import com.course.ecommerce.service.IdempotencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final int EXPIRE_HOURS = 24;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean isDuplicate(String idempotencyKey) {
        String key = IDEMPOTENCY_PREFIX + idempotencyKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public void markProcessing(String idempotencyKey, String orderNo) {
        String key = IDEMPOTENCY_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(key, "PROCESSING:" + orderNo, EXPIRE_HOURS, TimeUnit.HOURS);
    }

    @Override
    public void markCompleted(String idempotencyKey) {
        String key = IDEMPOTENCY_PREFIX + idempotencyKey;
        String currentValue = redisTemplate.opsForValue().get(key);
        if (currentValue != null && currentValue.startsWith("PROCESSING:")) {
            String orderNo = currentValue.substring("PROCESSING:".length());
            redisTemplate.opsForValue().set(key, "COMPLETED:" + orderNo, EXPIRE_HOURS, TimeUnit.HOURS);
        }
    }

    @Override
    public String getOrderNo(String idempotencyKey) {
        String key = IDEMPOTENCY_PREFIX + idempotencyKey;
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            int idx = value.indexOf(':');
            if (idx > 0) {
                return value.substring(idx + 1);
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add order-service/src/main/java/com/course/ecommerce/controller/SeckillController.java
git add order-service/src/main/java/com/course/ecommerce/service/IdempotencyService.java
git add order-service/src/main/java/com/course/ecommerce/service/impl/IdempotencyServiceImpl.java
git commit -m "feat: add seckill API and idempotency service"
```

---

## PART F: Kafka消费者与订单创建

### Task F1: Create Kafka Consumer and Order Creation

**Files:**
- Create: `order-service/src/main/java/com/course/ecommerce/config/KafkaConsumerConfig.java`
- Create: `order-service/src/main/java/com/course/ecommerce/consumer/SeckillOrderConsumer.java`
- Modify: `order-service/src/main/java/com/course/ecommerce/service/OrderService.java`
- Modify: `order-service/src/main/java/com/course/ecommerce/service/impl/OrderServiceImpl.java`

- [ ] **Step 1: Create KafkaConsumerConfig**

```java
package com.course.ecommerce.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:order-service}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

        JsonDeserializer<Object> deserializer = new JsonDeserializer<>();
        deserializer.addTrustedPackages("com.course.ecommerce.event");

        return new DefaultKafkaConsumerFactory<>(config,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setBatchListener(false);
        return factory;
    }
}
```

- [ ] **Step 2: Modify OrderService interface**

Modify: `order-service/src/main/java/com/course/ecommerce/service/OrderService.java`

Add method:
```java
package com.course.ecommerce.service;

import com.course.ecommerce.entity.Order;
import com.course.ecommerce.event.OrderCreateEvent;

public interface OrderService {

    Order getOrderById(Long id);

    /**
     * 从秒杀事件创建订单
     * @param event 订单创建事件
     * @return 创建的订单
     */
    Order createOrderFromSeckill(OrderCreateEvent event);
}
```

- [ ] **Step 3: Update OrderServiceImpl**

Modify: `order-service/src/main/java/com/course/ecommerce/service/impl/OrderServiceImpl.java`

```java
package com.course.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.course.ecommerce.common.exception.BusinessException;
import com.course.ecommerce.entity.Order;
import com.course.ecommerce.event.OrderCreateEvent;
import com.course.ecommerce.mapper.OrderMapper;
import com.course.ecommerce.service.IdempotencyService;
import com.course.ecommerce.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private IdempotencyService idempotencyService;

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
    public Order createOrderFromSeckill(OrderCreateEvent event) {
        // 1. 再次检查幂等性（DB兜底）
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.eq("order_no", event.getOrderNo());
        if (orderMapper.selectCount(wrapper) > 0) {
            log.warn("Order already exists: {}", event.getOrderNo());
            return orderMapper.selectOne(wrapper);
        }

        // 2. 创建订单
        Order order = new Order();
        order.setOrderNo(event.getOrderNo());
        order.setUserId(event.getUserId());
        order.setProductId(event.getProductId());
        order.setQuantity(event.getQuantity());
        order.setTotalAmount(event.getTotalAmount());
        order.setStatus("CREATED");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        orderMapper.insert(order);

        // 3. 标记幂等性Key为已完成
        idempotencyService.markCompleted(event.getIdempotencyKey());

        log.info("Order created successfully: orderNo={}", event.getOrderNo());
        return order;
    }
}
```

- [ ] **Step 4: Create SeckillOrderConsumer**

```java
package com.course.ecommerce.consumer;

import com.course.ecommerce.event.OrderCreateEvent;
import com.course.ecommerce.service.CompensationService;
import com.course.ecommerce.service.OrderService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class SeckillOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderConsumer.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private CompensationService compensationService;

    @KafkaListener(topics = "${kafka.topic.seckill-order:seckill-order}",
            groupId = "${spring.kafka.consumer.group-id:order-service}")
    public void consume(ConsumerRecord<String, Object> record, Acknowledgment acknowledgment) {
        OrderCreateEvent event = (OrderCreateEvent) record.value();
        log.info("Received OrderCreateEvent: orderNo={}, partition={}",
                event.getOrderNo(), record.partition());

        try {
            // 创建订单
            orderService.createOrderFromSeckill(event);
            acknowledgment.acknowledge();
            log.info("OrderCreateEvent processed successfully: orderNo={}", event.getOrderNo());
        } catch (Exception e) {
            log.error("OrderCreateEvent processing failed: orderNo={}, error={}",
                    event.getOrderNo(), e.getMessage());
            // 触发补偿
            compensationService.compensateOrderCreation(event);
            acknowledgment.acknowledge();
        }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add order-service/src/main/java/com/course/ecommerce/config/KafkaConsumerConfig.java
git add order-service/src/main/java/com/course/ecommerce/consumer/
git add order-service/src/main/java/com/course/ecommerce/service/
git commit -m "feat: add Kafka consumer and async order creation"
```

---

## PART G: 补偿服务与库存回滚

### Task G1: Create Compensation Service

**Files:**
- Create: `order-service/src/main/java/com/course/ecommerce/service/CompensationService.java`
- Create: `order-service/src/main/java/com/course/ecommerce/service/impl/CompensationServiceImpl.java`
- Create: `inventory-service/src/main/java/com/course/ecommerce/controller/InventoryInternalController.java`

- [ ] **Step 1: Create CompensationService interface**

```java
package com.course.ecommerce.service;

import com.course.ecommerce.event.OrderCreateEvent;

/**
 * 补偿服务
 * 处理订单创建失败时的补偿逻辑
 */
public interface CompensationService {

    /**
     * 补偿订单创建失败
     * 1. 回滚Redis库存
     * 2. 清理幂等性标记
     */
    void compensateOrderCreation(OrderCreateEvent event);
}
```

- [ ] **Step 2: Create CompensationServiceImpl**

```java
package com.course.ecommerce.service.impl;

import com.course.ecommerce.event.OrderCreateEvent;
import com.course.ecommerce.service.CompensationService;
import com.course.ecommerce.service.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class CompensationServiceImpl implements CompensationService {

    private static final Logger log = LoggerFactory.getLogger(CompensationServiceImpl.class);

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${inventory.service.url:http://inventory-service:8084}")
    private String inventoryServiceUrl;

    @Override
    public void compensateOrderCreation(OrderCreateEvent event) {
        log.info("Compensating order creation: orderNo={}", event.getOrderNo());

        try {
            // 1. 回滚库存
            rollbackStock(event.getProductId(), event.getQuantity());

            // 2. 清理幂等性标记（允许用户重新参与）
            // 注意：这里不清理，让用户保留"已参与"状态，避免重复扣库存
            // 如果需要允许重试，可以清理幂等性标记

            log.info("Compensation completed: orderNo={}", event.getOrderNo());
        } catch (Exception e) {
            log.error("Compensation failed: orderNo={}, error={}", event.getOrderNo(), e.getMessage());
            // TODO: 发送告警，人工介入处理
        }
    }

    private void rollbackStock(Long productId, Integer quantity) {
        try {
            String url = String.format("%s/api/inventory/internal/rollback/%d/%d",
                    inventoryServiceUrl, productId, quantity);
            restTemplate.postForObject(url, null, String.class);
            log.info("Stock rollback success: productId={}, quantity={}", productId, quantity);
        } catch (Exception e) {
            log.error("Stock rollback failed: productId={}, error={}", productId, e.getMessage());
            throw e;
        }
    }
}
```

- [ ] **Step 3: Create InventoryInternalController**

```java
package com.course.ecommerce.controller;

import com.course.ecommerce.service.SeckillStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部库存接口（服务间调用）
 */
@RestController
@RequestMapping("/api/inventory/internal")
public class InventoryInternalController {

    private static final Logger log = LoggerFactory.getLogger(InventoryInternalController.class);

    @Autowired
    private SeckillStockService seckillStockService;

    @PostMapping("/rollback/{productId}/{quantity}")
    public String rollbackStock(
            @PathVariable Long productId,
            @PathVariable Integer quantity) {
        log.info("Internal rollback request: productId={}, quantity={}", productId, quantity);
        seckillStockService.rollbackStock(productId, quantity);
        return "OK";
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add order-service/src/main/java/com/course/ecommerce/service/CompensationService.java
git add order-service/src/main/java/com/course/ecommerce/service/impl/CompensationServiceImpl.java
git add inventory-service/src/main/java/com/course/ecommerce/controller/InventoryInternalController.java
git commit -m "feat: add compensation service and stock rollback"
```

---

## PART H: Database Schema Update

### Task H1: Update SQL Schema for Unique Index

**Files:**
- Modify: `sql/init.sql`

- [ ] **Step 1: Add unique index to t_order table**

Add to `sql/init.sql` after t_order table creation:

```sql
-- 为秒杀场景添加唯一索引（活动期间同一用户同一商品只能下一单）
ALTER TABLE t_order ADD UNIQUE INDEX uk_user_product_activity
    (user_id, product_id, seckill_activity_id);
```

Note: First need to add seckill_activity_id column to Order entity if not exists.

- [ ] **Step 2: Update Order entity**

Modify: `order-service/src/main/java/com/course/ecommerce/entity/Order.java`

Add field:
```java
@Column("seckill_activity_id")
private Long seckillActivityId;
```

- [ ] **Step 3: Commit**

```bash
git add sql/init.sql
git add order-service/src/main/java/com/course/ecommerce/entity/Order.java
git commit -m "feat: add unique index for seckill idempotency"
```

---

## PART I: Docker Compose Integration

### Task I1: Add Kafka and Zookeeper to Docker Compose

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: Add zookeeper service**

Add to `docker-compose.yml`:

```yaml
  # -----------------------------------------------
  # Zookeeper (for Kafka)
  # -----------------------------------------------
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    networks:
      - ecommerce-net

  # -----------------------------------------------
  # Kafka
  # -----------------------------------------------
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    networks:
      - ecommerce-net
```

- [ ] **Step 2: Add Kafka dependency to order-service and inventory-service**

Update `docker-compose.yml` order-service and inventory-service depends_on:

```yaml
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_started
```

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "feat: add Kafka and Zookeeper to docker-compose"
```

---

## PART J: JMeter Test Script

### Task J1: Create Seckill Test Script

**Files:**
- Create: `jmeter/seckill-test.jmx`

- [ ] **Step 1: Create seckill-test.jmx**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Seckill Order Test" enabled="true">
      <stringProp name="TestPlan.comments">秒杀下单压测脚本</stringProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Seckill Users" enabled="true">
        <stringProp name="ThreadGroup.num_threads">1000</stringProp>
        <stringProp name="ThreadGroup.ramp_time">10</stringProp>
        <stringProp name="ThreadGroup.duration">60</stringProp>
        <boolProp name="ThreadGroup.same_user_on_next_iteration">false</boolProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Seckill Order Request" enabled="true">
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="" elementType="HTTPArgument">
                <boolProp name="HTTPArgument.always_encode">false</boolProp>
                <stringProp name="Argument.value">{&quot;productId&quot;:1,&quot;seckillActivityId&quot;:1001,&quot;quantity&quot;:1,&quot;totalAmount&quot;:5999.00}</stringProp>
                <stringProp name="Argument.metadata">=</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">80</stringProp>
          <stringProp name="HTTPSampler.path">/api/orders/seckill</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <stringProp name="HTTPSampler.contentEncoding">UTF-8</stringProp>
        </HTTPSamplerProxy>
        <hashTree>
          <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="HTTP Headers" enabled="true">
            <collectionProp name="HeaderManager.headers">
              <elementProp name="Content-Type" elementType="Header">
                <stringProp name="Header.name">Content-Type</stringProp>
                <stringProp name="Header.value">application/json</stringProp>
              </elementProp>
              <elementProp name="X-User-Id" elementType="Header">
                <stringProp name="Header.name">X-User-Id</stringProp>
                <stringProp name="Header.value">${__Random(1,1000)}</stringProp>
              </elementProp>
            </collectionProp>
          </HeaderManager>
        </hashTree>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

- [ ] **Step 2: Commit**

```bash
git add jmeter/seckill-test.jmx
git commit -m "test: add JMeter seckill test script"
```

---

## SELF-REVIEW CHECKLIST

- [ ] **Spec Coverage:** 所有需求都有对应任务实现
  - Redis缓存库存 → Task C1
  - Kafka异步处理 → Task D1, F1
  - 雪花算法订单ID → Task A1
  - 幂等性保证 → Task E1, H1
  - 数据一致性 → Task G1

- [ ] **Placeholder Scan:** 无"TBD"/"TODO"/"实现细节稍后补充"

- [ ] **Type Consistency:**
  - SnowflakeIdGenerator.nextId() 返回 long
  - IdGenUtil.generateOrderNo() 返回 String
  - OrderCreateEvent 所有字段类型一致

- [ ] **Path Consistency:**
  - 所有文件路径使用正确的项目结构
  - common-core, order-service, inventory-service 路径正确

---

## EXECUTION HANDOFF

**Plan complete and saved to `docs/superpowers/plans/2026-04-11-seckill-order-implementation.md`.**

**Two execution options:**

**1. Subagent-Driven (recommended)** - Dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints for review

**Which approach would you prefer?**
