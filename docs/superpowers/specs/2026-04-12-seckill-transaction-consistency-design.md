# 秒杀系统事务与一致性设计方案

## 背景

秒杀系统中，订单服务与库存服务是独立微服务，各自拥有独立数据库。当前已实现Redis预扣减库存、幂等性保障、基于Kafka的异步订单创建和基础补偿机制。但**支付流程缺失**，导致订单状态流转不完整；**库存最终一致性**未解决（Redis扣减后未同步到MySQL）。

## 最终目标

完成秒杀订单从创建到支付的完整生命周期管理，实现"Redis预扣 → 订单创建 → 支付回调 → 库存确认 → 超时回滚"的全流程最终一致性。

## 设计决策

### 同步 vs 批量异步选择

| 维度 | 同步单笔异步化 | 批量异步 |
|------|---------------|---------|
| 实时性 | 秒级 | 分钟级 |
| DB压力 | 中等（Kafka削峰后） | 低 |
| 实现复杂度 | 低 | 高（需分布式任务、对账） |
| 故障定位 | 单笔易追踪 | 批量失败影响面广 |

**决策：采用同步单笔异步化（Kafka消息）**

理由：
1. 秒杀是低频活动，Redis已扛住峰值，DB不需要批量削峰
2. 库存准确性优先，秒级延迟可接受，分钟级延迟有风险
3. 单笔故障隔离，易于对账和补偿
4. 实现简单，可水平扩展消费者

## 架构设计

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   用户请求   │────▶│  Seckill     │────▶│   Redis     │
└─────────────┘     │  Controller  │     │  Lua扣减    │
                    └──────────────┘     └──────┬──────┘
                                                 │
                    ┌──────────────┐            ▼
                    │   Kafka      │◄─── 发送订单创建事件
                    │ seckill-order│
                    └──────┬───────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  Order       │   │  Payment     │   │  Inventory   │
│  Consumer    │   │  Callback    │   │  Sync        │
│  (创建订单)   │   │  (支付回调)   │   │  (库存同步)   │
└──────┬───────┘   └──────┬───────┘   └──────┬───────┘
       │                  │                  │
       ▼                  ▼                  ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  t_order     │   │  更新状态PAID │   │  t_inventory │
│  PENDING     │   │      │       │   │  stock-=n    │
└──────────────┘   └──────┼───────┘   └──────────────┘
                          │
                          ▼
                    ┌──────────────┐
                    │   Kafka      │
                    │inventory-    │
                    │  deduct      │
                    └──────────────┘

超时取消流程：
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Scheduler  │────▶│  查询超时    │────▶│  更新CANCELLED│
│  (每5分钟)   │     │  PENDING>15m │     │      │       │
└──────────────┘     └──────────────┘     └──────┼───────┘
                                                  ▼
                                         ┌──────────────┐
                                         │  回滚Redis   │
                                         │  清理幂等Key │
                                         └──────────────┘
```

## 详细设计

### Step 1: 支付回调接口实现

**职责：**
- 接收mock支付结果回调
- 幂等性校验（paymentNo维度）
- 状态机校验（仅允许PENDING→PAID/CANCELLED）
- 支付成功后触发库存同步

**接口定义：**
```
POST /api/orders/payment/callback
Request:
{
  "orderNo": "ORDER202404120001",
  "paymentNo": "PAY202404120001",
  "status": "SUCCESS|FAILED",
  "amount": 8999.00,
  "payTime": 1712900000000
}

Response: 200 OK (幂等，重复调用返回成功)
```

**状态机：**
```
PENDING ──支付成功──▶ PAID ──▶ 发送库存扣减消息
    │
    └──支付失败/超时──▶ CANCELLED ──▶ 回滚Redis库存
```

**幂等性实现：**
- Redis Key: `payment:{paymentNo}`
- 24小时过期
- 已处理的paymentNo直接返回成功

### Step 2: 库存同步到DB（最终一致性）

**流程：**
1. 支付回调更新订单状态为PAID
2. 发送`InventoryDeductEvent`到Kafka `inventory-deduct` topic
3. inventory-service消费消息，使用乐观锁扣减MySQL库存

**消息格式：**
```java
public class InventoryDeductEvent {
    private String orderNo;
    private Long productId;
    private Integer quantity;
    private Long timestamp;
}
```

**MySQL扣减（乐观锁）：**
```sql
UPDATE t_inventory
SET stock = stock - ?, version = version + 1
WHERE product_id = ? AND stock >= ?
```

**消费策略：**
- 手动ACK
- 消费失败抛异常，Kafka自动重试（最多3次）
- 超过重试次数进入死信队列

### Step 3: 订单超时自动取消

**定时任务：**
- 执行频率：每5分钟
- 查询条件：`status = 'PENDING' AND created_at < NOW() - INTERVAL 15 MINUTE`

**处理流程（每条记录）：**
1. 更新订单状态为CANCELLED
2. 调用库存服务回滚Redis库存
3. 清理幂等性Key（允许用户重新秒杀）

**幂等性保证：**
- 取消操作记录`cancel:{orderNo}`到Redis
- 已取消的订单不再处理

### Step 4: 库存对账补偿（可选增强）

**定时对账：**
- 执行频率：每小时
- 对比Redis库存与MySQL库存
- 差异超过阈值时报警

## 数据模型变更

### t_order（已有，无需变更）
```sql
status: PENDING/PAID/SHIPPED/COMPLETED/CANCELLED
```

### t_inventory（已有，无需变更）
```sql
product_id, stock, reserved_stock, version(乐观锁)
```

### 新增：支付流水表（可选，日志追踪用）
```sql
CREATE TABLE t_payment_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL,
    payment_no VARCHAR(50) NOT NULL UNIQUE,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    pay_time DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

## 错误处理

| 场景 | 处理策略 |
|------|---------|
| 支付回调重复 | 幂等性校验，直接返回成功 |
| 支付回调订单不存在 | 记录错误日志，返回404 |
| 支付回调状态非法 | 返回400，不更新 |
| 库存同步消费失败 | 不ACK，Kafka重试，3次后进死信队列 |
| 库存同步库存不足 | 记录异常，人工介入（理论上不应发生） |
| 超时取消回滚失败 | 记录错误，定时任务下次重试 |

## 非目标

- 不实现真实支付通道（仅mock回调）
- 不改动已存在的秒杀核心逻辑（Redis Lua扣减、幂等性、基础补偿）
- 不引入Seata等分布式事务框架
- 不修改用户服务、商品服务
- 不处理配送/物流状态（SHIPPED及之后）

## 验收标准

1. **支付回调：** 能正确更新订单状态，重复调用幂等
2. **库存同步：** 支付成功后MySQL库存正确扣减
3. **超时取消：** 15分钟未支付订单自动取消，Redis库存回滚
4. **全链路：** 完整跑通"秒杀下单→支付→库存同步"流程

## 实施计划

见并行执行计划，分为4个独立任务：
- Task 1: 支付回调接口
- Task 2: 库存同步消息
- Task 3: 超时取消定时任务
- Task 4: 集成测试
