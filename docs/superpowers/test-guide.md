# 秒杀系统事务与一致性测试指南

## 1. 单元测试

### 1.1 运行单元测试

```bash
# 测试支付服务
cd /Users/kalin/github/distributed_system_course
mvn test -pl order-service -Dtest=PaymentServiceTest

# 测试库存消费者
mvn test -pl inventory-service -Dtest=InventoryDeductConsumerTest
```

### 1.2 预期结果
- 所有测试用例通过
- 覆盖率 > 80%

---

## 2. 集成测试（Docker环境）

### 2.1 启动完整环境

```bash
# 构建项目
mvn clean package -DskipTests -pl order-service,inventory-service -am

# 启动Docker环境
docker compose up -d --build

# 等待服务就绪
sleep 30

# 检查服务状态
docker compose ps
```

### 2.2 全链路测试流程

#### Step 1: 预热库存

```bash
curl -X POST http://localhost:8084/api/inventory/seckill/warmup/1/100
```

**预期结果:**
```json
{
  "code": 200,
  "message": "库存预热成功",
  "data": null
}
```

#### Step 2: 秒杀下单

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

**预期结果:**
```json
{
  "code": 202,
  "message": "排队中",
  "orderNo": "ORDERxxxxxx",
  "status": "PROCESSING"
}
```

记录返回的 `orderNo`，后续步骤使用。

#### Step 3: 验证订单创建

等待5秒后查询订单：

```bash
curl "http://localhost:8085/api/orders/{orderNo}"
```

**预期结果:**
```json
{
  "code": 200,
  "data": {
    "orderNo": "ORDERxxxxxx",
    "status": "PENDING",
    "productId": 1,
    "quantity": 1
  }
}
```

#### Step 4: 支付回调（成功）

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

**预期结果:**
```json
{
  "code": 200,
  "message": "Payment processed",
  "data": null
}
```

#### Step 5: 验证订单状态更新

```bash
curl "http://localhost:8085/api/orders/{orderNo}"
```

**预期结果:**
```json
{
  "code": 200,
  "data": {
    "orderNo": "ORDERxxxxxx",
    "status": "PAID"
  }
}
```

#### Step 6: 验证库存同步

等待3秒后查询库存：

```bash
curl http://localhost:8084/api/inventory/1
```

**预期结果:**
```json
{
  "code": 200,
  "data": {
    "productId": 1,
    "stock": 99,  // 减少了1
    "reservedStock": 0
  }
}
```

#### Step 7: 验证支付幂等性

重复Step 4的支付回调请求：

```bash
curl -X POST http://localhost:8085/api/orders/payment/callback \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "{orderNo}",
    "paymentNo": "PAY001",
    "status": "SUCCESS",
    "amount": 8999.00
  }'
```

**预期结果:**
```json
{
  "code": 200,
  "message": "Payment processed"
}
```

订单状态应保持PAID，不应重复扣减库存。

---

## 3. 超时取消测试

### 3.1 创建新订单

```bash
curl -X POST http://localhost:8085/api/orders/seckill \
  -H "X-User-Id: 2" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 1,
    "seckillActivityId": 1001,
    "quantity": 1,
    "totalAmount": 8999.00
  }'
```

记录新订单号 `orderNo2`。

### 3.2 验证初始状态

```bash
curl "http://localhost:8085/api/orders/{orderNo2}"
```

**预期:** status为PENDING

### 3.3 等待超时

等待15分钟（或修改系统时间/配置缩短测试时间）。

**加速测试方法：**
临时修改 `OrderTimeoutScheduler.TIMEOUT_MINUTES = 1`，重新部署后等待1分钟。

### 3.4 验证自动取消

```bash
curl "http://localhost:8085/api/orders/{orderNo2}"
```

**预期结果:**
```json
{
  "code": 200,
  "data": {
    "orderNo": "ORDERxxxxxx",
    "status": "CANCELLED"
  }
}
```

### 3.5 验证库存回滚

```bash
curl http://localhost:8084/api/inventory/seckill/1
```

**预期结果:** 剩余库存恢复（如之前99，现在回到100）

---

## 4. 边界情况测试

### 4.1 重复秒杀测试

```bash
# 用户1再次秒杀同一商品
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

**预期结果:**
```json
{
  "code": 409,
  "message": "用户已参与"
}
```

### 4.2 库存不足测试

```bash
# 先设置库存为0
curl -X POST http://localhost:8084/api/inventory/seckill/warmup/2/0

# 新用户秒杀
curl -X POST http://localhost:8085/api/orders/seckill \
  -H "X-User-Id: 99" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 2,
    "seckillActivityId": 1001,
    "quantity": 1,
    "totalAmount": 8999.00
  }'
```

**预期结果:**
```json
{
  "code": 410,
  "message": "库存不足"
}
```

### 4.3 支付回调订单不存在

```bash
curl -X POST http://localhost:8085/api/orders/payment/callback \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "ORDER_NOT_EXIST",
    "paymentNo": "PAY999",
    "status": "SUCCESS",
    "amount": 8999.00
  }'
```

**预期结果:**
```json
{
  "code": 500,
  "message": "Failed to process payment"
}
```

---

## 5. Kafka消息测试

### 5.1 查看Kafka消息

```bash
# 进入Kafka容器
docker exec -it kafka bash

# 查看订单创建消息
kafka-console-consumer --bootstrap-server localhost:29092 \
  --topic seckill-order --from-beginning --max-messages 5

# 查看库存扣减消息
kafka-console-consumer --bootstrap-server localhost:29092 \
  --topic inventory-deduct --from-beginning --max-messages 5
```

### 5.2 验证消息顺序

确保消息顺序：
1. `seckill-order` 消息（创建订单）
2. `inventory-deduct` 消息（支付成功后）

---

## 6. 性能测试

### 6.1 并发支付回调

```bash
# 先创建多个订单，然后并发支付
for i in {1..10}; do
  curl -X POST http://localhost:8085/api/orders/payment/callback \
    -H "Content-Type: application/json" \
    -d "{\"orderNo\": \"ORDER$i\", \"paymentNo\": \"PAY$i\", \"status\": \"SUCCESS\", \"amount\": 8999.00}" &
done
wait
```

**预期:** 所有请求正确处理，无重复扣减

### 6.2 日志检查

```bash
# 查看order-service日志
docker logs order-service | grep -E "(Payment|Inventory)"

# 查看inventory-service日志
docker logs inventory-service | grep -E "(Inventory|deduct)"
```

---

## 7. 故障恢复测试

### 7.1 inventory-service停机测试

```bash
# 停止inventory-service
docker stop inventory-service

# 支付回调（应失败，但订单状态应已更新）
curl -X POST http://localhost:8085/api/orders/payment/callback \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "ORDER_TEST",
    "paymentNo": "PAY_TEST",
    "status": "SUCCESS",
    "amount": 8999.00
  }'

# 启动inventory-service
docker start inventory-service

# 库存扣减消息应被消费（Kafka持久化）
sleep 5
docker logs inventory-service | grep "Inventory deducted"
```

---

## 8. 清理测试数据

```bash
# 清空Redis数据（测试环境）
docker exec redis redis-cli -a redis123 FLUSHDB

# 重置MySQL数据
docker compose down -v
docker compose up -d
```

---

## 9. 自动化测试脚本

创建一键测试脚本：

```bash
#!/bin/bash
# test-seckill-transaction.sh

set -e

echo "=== Step 1: 预热库存 ==="
curl -s -X POST http://localhost:8084/api/inventory/seckill/warmup/1/100

echo -e "\n=== Step 2: 秒杀下单 ==="
ORDER_RESPONSE=$(curl -s -X POST http://localhost:8085/api/orders/seckill \
  -H "X-User-Id: 999" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"seckillActivityId":1001,"quantity":1,"totalAmount":8999.00}')
ORDER_NO=$(echo $ORDER_RESPONSE | grep -o '"orderNo":"[^"]*"' | cut -d'"' -f4)
echo "Order No: $ORDER_NO"

echo -e "\n=== Step 3: 等待订单创建 ==="
sleep 3

echo -e "\n=== Step 4: 支付回调 ==="
curl -s -X POST http://localhost:8085/api/orders/payment/callback \
  -H "Content-Type: application/json" \
  -d "{\"orderNo\":\"$ORDER_NO\",\"paymentNo\":\"PAY_$(date +%s)\",\"status\":\"SUCCESS\",\"amount\":8999.00}"

echo -e "\n=== Step 5: 验证订单状态 ==="
curl -s "http://localhost:8085/api/orders/$ORDER_NO"

echo -e "\n=== Test Completed ==="
```

---

## 10. 测试通过标准

| 测试项 | 通过标准 |
|--------|----------|
| 单元测试 | 全部通过，覆盖率>80% |
| 支付回调 | 订单状态PENDING→PAID，幂等性正确 |
| 库存同步 | MySQL库存与Redis扣减一致 |
| 超时取消 | 15分钟后自动CANCELLED，库存回滚 |
| 并发测试 | 100并发支付无重复扣减 |
| 故障恢复 | inventory-service重启后消息正常消费 |
