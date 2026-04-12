# 秒杀系统事务与一致性实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐秒杀订单从创建到支付的完整生命周期管理，实现"Redis预扣 → 订单创建 → 支付回调 → 库存确认 → 超时回滚"的全流程最终一致性。

**架构:** 采用同步单笔异步化（Kafka消息）实现最终一致性，支付回调立即返回，库存同步异步处理，超时取消通过定时任务扫描。

**Tech Stack:** Spring Boot 3.2, Kafka, MySQL, Redis, MyBatis-Plus

---

## 文件结构

### 新建文件
- `order-service/src/main/java/com/course/ecommerce/dto/PaymentCallbackRequest.java` - 支付回调请求DTO
- `order-service/src/main/java/com/course/ecommerce/controller/PaymentController.java` - 支付回调接口
- `order-service/src/main/java/com/course/ecommerce/service/PaymentService.java` - 支付服务接口
- `order-service/src/main/java/com/course/ecommerce/service/impl/PaymentServiceImpl.java` - 支付服务实现
- `order-service/src/main/java/com/course/ecommerce/dto/InventoryDeductEvent.java` - 库存扣减事件
- `order-service/src/main/java/com/course/ecommerce/producer/InventoryDeductProducer.java` - 库存扣减生产者
- `inventory-service/src/main/java/com/course/ecommerce/dto/InventoryDeductEvent.java` - 库存扣减事件（共享DTO）
- `inventory-service/src/main/java/com/course/ecommerce/consumer/InventoryDeductConsumer.java` - 库存扣减消费者
- `order-service/src/main/java/com/course/ecommerce/scheduler/OrderTimeoutScheduler.java` - 超时订单定时任务
- `order-service/src/main/java/com/course/ecommerce/service/OrderCancelService.java` - 订单取消服务接口
- `order-service/src/main/java/com/course/ecommerce/service/impl/OrderCancelServiceImpl.java` - 订单取消服务实现

### 修改文件
- `order-service/src/main/java/com/course/ecommerce/entity/Order.java` - 确认status字段包含PENDING/PAID/CANCELLED
- `order-service/src/main/java/com/course/ecommerce/mapper/OrderMapper.java` - 添加按状态和时间查询方法
- `order-service/src/main/java/com/course/ecommerce/config/KafkaProducerConfig.java` - 添加inventory-deduct topic配置
- `inventory-service/src/main/java/com/course/ecommerce/config/KafkaConsumerConfig.java` - 添加inventory-deduct消费者配置

---

## Task 1: 支付回调接口实现

**依赖:** 无（独立任务）
**目标:** 创建支付回调接口，接收mock支付结果，更新订单状态，保证幂等性

**Files:**
- Create: `order-service/src/main/java/com/course/ecommerce/dto/PaymentCallbackRequest.java`
- Create: `order-service/src/main/java/com/course/ecommerce/controller/PaymentController.java`
- Create: `order-service/src/main/java/com/course/ecommerce/service/PaymentService.java`
- Create: `order-service/src/main/java/com/course/ecommerce/service/impl/PaymentServiceImpl.java`
- Modify: `order-service/src/main/java/com/course/ecommerce/mapper/OrderMapper.java`

### Step 1.1: 创建支付回调请求DTO

**File:** `order-service/src/main/java/com/course/ecommerce/dto/PaymentCallbackRequest.java`

```java
package com.course.ecommerce.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentCallbackRequest {
    private String orderNo;
    private String paymentNo;
    private String status;  // SUCCESS or FAILED
    private BigDecimal amount;
    private Long payTime;
}
```

**Verification:** 文件创建成功，无编译错误

### Step 1.2: 创建PaymentService接口

**File:** `order-service/src/main/java/com/course/ecommerce/service/PaymentService.java`

```java
package com.course.ecommerce.service;

import com.course.ecommerce.dto.PaymentCallbackRequest;

public interface PaymentService {
    /**
     * 处理支付回调
     * @param request 支付回调请求
     * @return true-处理成功, false-处理失败
     */
    boolean handlePaymentCallback(PaymentCallbackRequest request);
}
```

**Verification:** 文件创建成功，无编译错误

### Step 1.3: 创建PaymentServiceImpl实现

**File:** `order-service/src/main/java/com/course/ecommerce/service/impl/PaymentServiceImpl.java`

```java
package com.course.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.course.ecommerce.dto.PaymentCallbackRequest;
import com.course.ecommerce.entity.Order;
import com.course.ecommerce.mapper.OrderMapper;
import com.course.ecommerce.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderMapper orderMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String PAYMENT_KEY_PREFIX = "payment:";
    private static final long PAYMENT_EXPIRE_HOURS = 24;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handlePaymentCallback(PaymentCallbackRequest request) {
        String paymentNo = request.getPaymentNo();
        String orderNo = request.getOrderNo();

        // 1. 幂等性检查
        String paymentKey = PAYMENT_KEY_PREFIX + paymentNo;
        Boolean isNew = stringRedisTemplate.opsForValue()
                .setIfAbsent(paymentKey, "1", Duration.ofHours(PAYMENT_EXPIRE_HOURS));
        if (Boolean.FALSE.equals(isNew)) {
            log.info("Payment callback duplicate, paymentNo: {}", paymentNo);
            return true;
        }

        // 2. 查询订单
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getOrderNo, orderNo);
        Order order = orderMapper.selectOne(queryWrapper);

        if (order == null) {
            log.error("Order not found, orderNo: {}", orderNo);
            return false;
        }

        // 3. 状态机校验
        if (!"PENDING".equals(order.getStatus())) {
            log.warn("Order status not PENDING, orderNo: {}, status: {}", orderNo, order.getStatus());
            return false;
        }

        // 4. 更新订单状态
        String newStatus = "SUCCESS".equals(request.getStatus()) ? "PAID" : "CANCELLED";
        LambdaUpdateWrapper<Order> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Order::getOrderNo, orderNo)
                .set(Order::getStatus, newStatus)
                .set(Order::getUpdatedAt, LocalDateTime.now());

        if ("PAID".equals(newStatus) && request.getPayTime() != null) {
            updateWrapper.set(Order::getCreatedAt,
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(request.getPayTime()), ZoneId.systemDefault()));
        }

        int updated = orderMapper.update(null, updateWrapper);
        if (updated == 0) {
            log.error("Failed to update order status, orderNo: {}", orderNo);
            return false;
        }

        log.info("Payment callback processed, orderNo: {}, paymentNo: {}, newStatus: {}",
                orderNo, paymentNo, newStatus);

        // 5. TODO: 支付成功后触发库存同步（Task 2实现）

        return true;
    }
}
```

**Verification:** 文件创建成功，无编译错误

### Step 1.4: 创建PaymentController

**File:** `order-service/src/main/java/com/course/ecommerce/controller/PaymentController.java`

```java
package com.course.ecommerce.controller;

import com.course.ecommerce.common.Result;
import com.course.ecommerce.dto.PaymentCallbackRequest;
import com.course.ecommerce.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/orders/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/callback")
    public ResponseEntity<?> paymentCallback(@RequestBody PaymentCallbackRequest request) {
        log.info("Received payment callback, orderNo: {}, paymentNo: {}, status: {}",
                request.getOrderNo(), request.getPaymentNo(), request.getStatus());

        if (request.getOrderNo() == null || request.getPaymentNo() == null || request.getStatus() == null) {
            return ResponseEntity.badRequest().body(Result.fail(400, "Missing required fields"));
        }

        boolean success = paymentService.handlePaymentCallback(request);

        if (success) {
            return ResponseEntity.ok(Result.success("Payment processed"));
        } else {
            return ResponseEntity.status(500).body(Result.fail(500, "Failed to process payment"));
        }
    }
}
```

**Verification:** 文件创建成功，无编译错误

### Step 1.5: 添加OrderMapper查询方法

**File:** `order-service/src/main/java/com/course/ecommerce/mapper/OrderMapper.java`

添加以下方法：

```java
package com.course.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.ecommerce.entity.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT * FROM t_order WHERE status = #{status} AND created_at < #{beforeTime}")
    List<Order> selectByStatusAndCreatedBefore(@Param("status") String status, @Param("beforeTime") LocalDateTime beforeTime);
}
```

**Verification:** 编译通过

### Step 1.6: Commit

```bash
git add order-service/src/main/java/com/course/ecommerce/dto/PaymentCallbackRequest.java
git add order-service/src/main/java/com/course/ecommerce/controller/PaymentController.java
git add order-service/src/main/java/com/course/ecommerce/service/PaymentService.java
git add order-service/src/main/java/com/course/ecommerce/service/impl/PaymentServiceImpl.java
git add order-service/src/main/java/com/course/ecommerce/mapper/OrderMapper.java
git commit -m "feat: add payment callback API with idempotency"
```

---

## Task 2: 库存同步到DB（最终一致性）

**依赖:** Task 1（需要PaymentService中触发库存同步的TODO位置）
**目标:** 支付成功后，异步将Redis预扣的库存同步扣减到MySQL库存表

**Files:**
- Create: `order-service/src/main/java/com/course/ecommerce/dto/InventoryDeductEvent.java`
- Create: `order-service/src/main/java/com/course/ecommerce/producer/InventoryDeductProducer.java`
- Create: `inventory-service/src/main/java/com/course/ecommerce/dto/InventoryDeductEvent.java`
- Create: `inventory-service/src/main/java/com/course/ecommerce/consumer/InventoryDeductConsumer.java`
- Modify: `order-service/src/main/java/com/course/ecommerce/service/impl/PaymentServiceImpl.java`
- Modify: `order-service/src/main/java/com/course/ecommerce/config/KafkaProducerConfig.java`
- Modify: `inventory-service/src/main/java/com/course/ecommerce/config/KafkaConsumerConfig.java`

### Step 2.1: 创建InventoryDeductEvent（order-service）

**File:** `order-service/src/main/java/com/course/ecommerce/dto/InventoryDeductEvent.java`

```java
package com.course.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDeductEvent {
    private String orderNo;
    private Long productId;
    private Integer quantity;
    private Long timestamp;
}
```

**Verification:** 文件创建成功

### Step 2.2: 创建InventoryDeductProducer

**File:** `order-service/src/main/java/com/course/ecommerce/producer/InventoryDeductProducer.java`

```java
package com.course.ecommerce.producer;

import com.course.ecommerce.dto.InventoryDeductEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryDeductProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "inventory-deduct";

    public void sendInventoryDeductEvent(InventoryDeductEvent event) {
        log.info("Sending inventory deduct event, orderNo: {}, productId: {}",
                event.getOrderNo(), event.getProductId());

        kafkaTemplate.send(TOPIC, event.getOrderNo(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send inventory deduct event, orderNo: {}",
                                event.getOrderNo(), ex);
                    } else {
                        log.info("Inventory deduct event sent successfully, orderNo: {}",
                                event.getOrderNo());
                    }
                });
    }
}
```

**Verification:** 文件创建成功

### Step 2.3: 修改PaymentServiceImpl触发库存同步

**File:** `order-service/src/main/java/com/course/ecommerce/service/impl/PaymentServiceImpl.java`

添加依赖注入和触发逻辑：

```java
// 在类顶部添加依赖
private final InventoryDeductProducer inventoryDeductProducer;

// 在handlePaymentCallback方法中，替换TODO部分
// 5. 支付成功后触发库存同步
if ("PAID".equals(newStatus)) {
    InventoryDeductEvent event = InventoryDeductEvent.builder()
            .orderNo(orderNo)
            .productId(order.getProductId())
            .quantity(order.getQuantity())
            .timestamp(System.currentTimeMillis())
            .build();
    inventoryDeductProducer.sendInventoryDeductEvent(event);
}
```

**Verification:** 编译通过

### Step 2.4: 复制InventoryDeductEvent到inventory-service

**File:** `inventory-service/src/main/java/com/course/ecommerce/dto/InventoryDeductEvent.java`

内容与order-service版本完全相同（实际项目中可放在common-core，但这里保持独立）。

**Verification:** 文件创建成功

### Step 2.5: 创建InventoryDeductConsumer

**File:** `inventory-service/src/main/java/com/course/ecommerce/consumer/InventoryDeductConsumer.java`

```java
package com.course.ecommerce.consumer;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.course.ecommerce.dto.InventoryDeductEvent;
import com.course.ecommerce.entity.Inventory;
import com.course.ecommerce.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryDeductConsumer {

    private final InventoryMapper inventoryMapper;

    @KafkaListener(topics = "inventory-deduct", containerFactory = "kafkaListenerContainerFactory")
    @Transactional(rollbackFor = Exception.class)
    public void consume(InventoryDeductEvent event, Acknowledgment acknowledgment) {
        log.info("Received inventory deduct event, orderNo: {}, productId: {}, quantity: {}",
                event.getOrderNo(), event.getProductId(), event.getQuantity());

        try {
            // 使用乐观锁扣减库存
            LambdaUpdateWrapper<Inventory> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Inventory::getProductId, event.getProductId())
                    .ge(Inventory::getStock, event.getQuantity())  // 库存充足
                    .setSql("stock = stock - " + event.getQuantity());

            int updated = inventoryMapper.update(null, updateWrapper);

            if (updated == 0) {
                // 库存不足或商品不存在，记录错误
                log.error("Failed to deduct inventory, insufficient stock or product not found, " +
                        "orderNo: {}, productId: {}, quantity: {}",
                        event.getOrderNo(), event.getProductId(), event.getQuantity());
                // 抛异常让Kafka重试
                throw new RuntimeException("Insufficient stock for product: " + event.getProductId());
            }

            log.info("Inventory deducted successfully, orderNo: {}, productId: {}, quantity: {}",
                    event.getOrderNo(), event.getProductId(), event.getQuantity());

            // 确认消息
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process inventory deduct event, orderNo: {}", event.getOrderNo(), e);
            // 不确认消息，让Kafka重试
            throw e;
        }
    }
}
```

**Verification:** 文件创建成功

### Step 2.6: 添加Kafka配置（inventory-service）

**File:** `inventory-service/src/main/java/com/course/ecommerce/config/KafkaConsumerConfig.java`

确保已有或添加以下配置：

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
        ConsumerFactory<String, Object> consumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    // 手动确认
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    // 重试3次
    factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3)));
    return factory;
}
```

**Verification:** 编译通过

### Step 2.7: Commit

```bash
git add order-service/src/main/java/com/course/ecommerce/dto/InventoryDeductEvent.java
git add order-service/src/main/java/com/course/ecommerce/producer/InventoryDeductProducer.java
git add order-service/src/main/java/com/course/ecommerce/service/impl/PaymentServiceImpl.java
git add inventory-service/src/main/java/com/course/ecommerce/dto/InventoryDeductEvent.java
git add inventory-service/src/main/java/com/course/ecommerce/consumer/InventoryDeductConsumer.java
git commit -m "feat: add inventory sync to DB after payment"
```

---

## Task 3: 订单超时自动取消

**依赖:** Task 1（需要OrderMapper的查询方法）
**目标:** 定时任务扫描超过15分钟未支付的PENDING订单，自动取消并回滚Redis库存

**Files:**
- Create: `order-service/src/main/java/com/course/ecommerce/scheduler/OrderTimeoutScheduler.java`
- Create: `order-service/src/main/java/com/course/ecommerce/service/OrderCancelService.java`
- Create: `order-service/src/main/java/com/course/ecommerce/service/impl/OrderCancelServiceImpl.java`
- Modify: `order-service/src/main/java/com/course/ecommerce/OrderServiceApplication.java` - 添加@EnableScheduling

### Step 3.1: 创建OrderCancelService接口

**File:** `order-service/src/main/java/com/course/ecommerce/service/OrderCancelService.java`

```java
package com.course.ecommerce.service;

import com.course.ecommerce.entity.Order;

public interface OrderCancelService {
    /**
     * 取消超时订单
     * @param order 订单
     * @return true-取消成功, false-取消失败
     */
    boolean cancelTimeoutOrder(Order order);
}
```

**Verification:** 文件创建成功

### Step 3.2: 创建OrderCancelServiceImpl

**File:** `order-service/src/main/java/com/course/ecommerce/service/impl/OrderCancelServiceImpl.java`

```java
package com.course.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.course.ecommerce.entity.Order;
import com.course.ecommerce.mapper.OrderMapper;
import com.course.ecommerce.service.IdempotencyService;
import com.course.ecommerce.service.OrderCancelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancelServiceImpl implements OrderCancelService {

    private final OrderMapper orderMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private final IdempotencyService idempotencyService;

    @Value("${inventory.service.url:http://inventory-service:8084}")
    private String inventoryServiceUrl;

    private static final String CANCEL_KEY_PREFIX = "cancel:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelTimeoutOrder(Order order) {
        String orderNo = order.getOrderNo();

        // 1. 幂等性检查
        String cancelKey = CANCEL_KEY_PREFIX + orderNo;
        Boolean isNew = stringRedisTemplate.opsForValue()
                .setIfAbsent(cancelKey, "1", Duration.ofHours(24));
        if (Boolean.FALSE.equals(isNew)) {
            log.info("Order already cancelled, orderNo: {}", orderNo);
            return true;
        }

        // 2. 更新订单状态为CANCELLED
        LambdaUpdateWrapper<Order> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Order::getId, order.getId())
                .eq(Order::getStatus, "PENDING")  // 乐观锁
                .set(Order::getStatus, "CANCELLED")
                .set(Order::getUpdatedAt, LocalDateTime.now());

        int updated = orderMapper.update(null, updateWrapper);
        if (updated == 0) {
            log.warn("Failed to cancel order, may be already processed, orderNo: {}", orderNo);
            return false;
        }

        log.info("Order cancelled, orderNo: {}", orderNo);

        // 3. 回滚Redis库存
        rollbackRedisStock(order);

        // 4. 清理幂等性Key（允许用户重新秒杀）
        clearIdempotencyKey(order);

        return true;
    }

    private void rollbackRedisStock(Order order) {
        try {
            String url = String.format("%s/api/inventory/internal/rollback/%s/%s",
                    inventoryServiceUrl, order.getProductId(), order.getQuantity());

            log.info("Rolling back stock, orderNo: {}, url: {}", order.getOrderNo(), url);
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Stock rollback successful, orderNo: {}", order.getOrderNo());
            } else {
                log.error("Stock rollback failed, orderNo: {}, status: {}",
                        order.getOrderNo(), response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to rollback stock, orderNo: {}", order.getOrderNo(), e);
            // 不回滚取消标记，下次定时任务不再处理，但库存回滚失败需要人工介入
        }
    }

    private void clearIdempotencyKey(Order order) {
        // 构造幂等性Key
        String idempotencyKey = String.format("%d:%d:%d",
                order.getUserId(), order.getProductId(), order.getSeckillActivityId());

        try {
            // 这里假设IdempotencyServiceImpl有清理方法，如果没有需要添加
            // 暂时直接操作Redis
            String key = "idempotency:" + idempotencyKey;
            stringRedisTemplate.delete(key);
            log.info("Idempotency key cleared, orderNo: {}", order.getOrderNo());
        } catch (Exception e) {
            log.error("Failed to clear idempotency key, orderNo: {}", order.getOrderNo(), e);
        }
    }
}
```

**Verification:** 编译通过

### Step 3.3: 创建OrderTimeoutScheduler

**File:** `order-service/src/main/java/com/course/ecommerce/scheduler/OrderTimeoutScheduler.java`

```java
package com.course.ecommerce.scheduler;

import com.course.ecommerce.entity.Order;
import com.course.ecommerce.mapper.OrderMapper;
import com.course.ecommerce.service.OrderCancelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final OrderMapper orderMapper;
    private final OrderCancelService orderCancelService;

    // 15分钟超时
    private static final long TIMEOUT_MINUTES = 15;

    @Scheduled(fixedRate = 5 * 60 * 1000)  // 每5分钟执行一次
    public void checkTimeoutOrders() {
        log.info("Checking timeout orders...");

        LocalDateTime timeoutBefore = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        List<Order> timeoutOrders = orderMapper.selectByStatusAndCreatedBefore("PENDING", timeoutBefore);

        log.info("Found {} timeout orders", timeoutOrders.size());

        for (Order order : timeoutOrders) {
            try {
                orderCancelService.cancelTimeoutOrder(order);
            } catch (Exception e) {
                log.error("Failed to cancel timeout order, orderNo: {}", order.getOrderNo(), e);
                // 继续处理其他订单
            }
        }

        log.info("Timeout orders check completed");
    }
}
```

**Verification:** 编译通过

### Step 3.4: 启用Scheduling

**File:** `order-service/src/main/java/com/course/ecommerce/OrderServiceApplication.java`

添加`@EnableScheduling`注解：

```java
package com.course.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

**Verification:** 编译通过

### Step 3.5: Commit

```bash
git add order-service/src/main/java/com/course/ecommerce/service/OrderCancelService.java
git add order-service/src/main/java/com/course/ecommerce/service/impl/OrderCancelServiceImpl.java
git add order-service/src/main/java/com/course/ecommerce/scheduler/OrderTimeoutScheduler.java
git add order-service/src/main/java/com/course/ecommerce/OrderServiceApplication.java
git commit -m "feat: add order timeout auto-cancel scheduler"
```

---

## Task 4: 集成测试

**依赖:** Task 1, 2, 3（全部完成）
**目标:** 验证完整流程：秒杀下单 → 支付回调 → 库存同步 → 超时取消

### Step 4.1: 构建项目

```bash
cd /Users/kalin/github/distributed_system_course
mvn clean package -DskipTests -pl order-service,inventory-service -am
```

**Expected:** BUILD SUCCESS

### Step 4.2: 启动Docker环境

```bash
docker compose up -d --build
sleep 30
```

### Step 4.3: 测试完整流程

**Step 4.3.1: 预热库存**

```bash
curl -X POST http://localhost:8084/api/inventory/seckill/warmup/1/100
```

**Expected:** 库存预热成功

**Step 4.3.2: 秒杀下单**

```bash
curl -X POST http://localhost:8085/api/orders/seckill \
  -H "X-User-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 1,
    "seckillActivityId": 1001,
    "quantity": 1,
    "totalAmount": 8999.00
  }'
```

**Expected:** 返回202排队中，获取orderNo

**Step 4.3.3: 等待订单创建（Kafka消费）**

```bash
sleep 5
curl http://localhost:8085/api/orders/{orderNo}
```

**Expected:** 订单状态为PENDING

**Step 4.3.4: 支付回调**

```bash
curl -X POST http://localhost:8085/api/orders/payment/callback \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "{orderNo}",
    "paymentNo": "PAY001",
    "status": "SUCCESS",
    "amount": 8999.00,
    "payTime": 1712900000000
  }'
```

**Expected:** 处理成功

**Step 4.3.5: 验证订单状态**

```bash
curl http://localhost:8085/api/orders/{orderNo}
```

**Expected:** 状态为PAID

**Step 4.3.6: 验证库存同步**

```bash
curl http://localhost:8084/api/inventory/1
```

**Expected:** MySQL库存已扣减（t_inventory.stock减少）

**Step 4.3.7: 测试幂等性（重复回调）**

重复Step 4.3.4的请求

**Expected:** 返回成功（幂等）

### Step 4.4: Commit

```bash
git add .
git commit -m "test: integration tests for seckill transaction flow"
```

---

## 非目标确认

- ✅ 不实现真实支付通道（仅mock回调）
- ✅ 不改动已存在的秒杀核心逻辑（Redis Lua扣减、幂等性、基础补偿）
- ✅ 不引入Seata等分布式事务框架
- ✅ 不修改用户服务、商品服务
- ✅ 不处理配送/物流状态（SHIPPED及之后）
