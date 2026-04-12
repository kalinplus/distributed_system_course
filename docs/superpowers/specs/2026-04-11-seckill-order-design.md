# 秒杀下单系统 - 任务卡 v2（阶段2：流程工程化）

## 背景
本项目是分布式系统课程的一部分，当前已完成基础微服务拆分、Redis缓存三防护、MySQL读写分离、ElasticSearch搜索等功能。order-service 和 inventory-service 已有基础CRUD能力，但缺少高并发秒杀场景下的核心能力。

本次任务是在现有基础上，实现完整的秒杀下单流程工程化，重点解决高并发下的库存扣减、流量削峰、幂等性和数据一致性问题。

## 最终目标
实现一个完整的秒杀下单系统：Redis缓存库存 + Lua原子扣减、Kafka异步处理订单（削峰填谷）、雪花算法订单ID、幂等性保证（活动期间同一用户同一商品限一次）、最终一致性保证（库存不超卖）。

---

## 分步计划（有序，每步独立可验收）

### Step 1: Redis库存预热与扣减服务

**做什么:** 在 inventory-service 中新增秒杀库存管理，包括库存预热（从MySQL加载到Redis）、Lua脚本原子扣减、库存回滚。

**产出物:**
- `inventory-service/src/main/java/com/course/ecommerce/service/SeckillStockService.java` - 秒杀库存服务接口
- `inventory-service/src/main/java/com/course/ecommerce/service/impl/SeckillStockServiceImpl.java` - 实现类
- `inventory-service/src/main/resources/scripts/stock_deduct.lua` - 原子扣减Lua脚本

**验收:**
- 调用预热接口后，Redis中存在 `seckill:stock:{productId}` key
- 并发调用扣减接口，库存扣减准确，不超卖
- Lua脚本执行返回剩余库存，库存为0时返回-1表示售罄

---

### Step 2: 雪花算法订单ID生成器

**做什么:** 在 common-core 中实现雪花算法，支持生成分布式唯一订单ID，预留基因法扩展点。

**产出物:**
- `common-core/src/main/java/com/course/ecommerce/util/SnowflakeIdGenerator.java` - 雪花算法实现
- `common-core/src/main/java/com/course/ecommerce/util/IdGenUtil.java` - ID生成工具类

**验收:**
- 生成10000个ID，无重复
- ID有序递增，支持反解析时间戳
- 支持自定义workerId/dataCenterId

---

### Step 3: Kafka消息DTO与幂等性Key设计

**做什么:** 在 common-core 中定义订单创建事件DTO，设计幂等性Key生成策略。

**产出物:**
- `common-core/src/main/java/com/course/ecommerce/event/OrderCreateEvent.java` - 订单创建事件
- `common-core/src/main/java/com/course/ecommerce/util/IdempotencyKeyUtil.java` - 幂等性Key工具

**验收:**
- OrderCreateEvent 包含所有创建订单所需字段
- 幂等性Key格式: `seckill:{userId}:{productId}:{activityId}`
- Key生成逻辑可复现（相同输入输出相同Key）

---

### Step 4: Kafka生产者与秒杀API

**做什么:** 在 order-service 中配置Kafka生产者，实现秒杀下单API（先扣Redis库存，再发消息）。

**产出物:**
- `order-service/src/main/java/com/course/ecommerce/config/KafkaProducerConfig.java` - Kafka生产者配置
- `order-service/src/main/java/com/course/ecommerce/producer/SeckillOrderProducer.java` - 秒杀订单生产者
- `order-service/src/main/java/com/course/ecommerce/controller/SeckillController.java` - 秒杀API

**验收:**
- 调用 `POST /api/orders/seckill` 返回排队中状态
- Redis库存成功扣减后，消息发送到Kafka topic `seckill-order`
- 同一用户同一商品多次请求，第二次返回"已参与"

---

### Step 5: Kafka消费者与订单创建

**做什么:** 在 order-service 中实现Kafka消费者，异步创建订单，处理失败补偿。

**产出物:**
- `order-service/src/main/java/com/course/ecommerce/config/KafkaConsumerConfig.java` - Kafka消费者配置
- `order-service/src/main/java/com/course/ecommerce/consumer/SeckillOrderConsumer.java` - 秒杀订单消费者
- `order-service/src/main/java/com/course/ecommerce/service/impl/OrderServiceImpl.java` - 修改：新增createOrderFromSeckill方法

**验收:**
- 消费消息后，订单表写入数据，订单号使用雪花ID
- 订单状态初始为PROCESSING，创建成功后更新为CREATED
- 消费失败时，消息进入死信队列或触发库存回滚

---

### Step 6: 幂等性保证与防重实现

**做什么:** 实现完整的幂等性检查：Redis层SETNX防重 + DB唯一索引兜底。

**产出物:**
- `sql/init.sql` - 修改：t_order表添加唯一索引 `(user_id, product_id, seckill_activity_id)`
- `order-service/src/main/java/com/course/ecommerce/service/IdempotencyService.java` - 幂等性检查服务
- `order-service/src/main/java/com/course/ecommerce/service/impl/IdempotencyServiceImpl.java` - 实现类

**验收:**
- 并发100次同一用户请求，只有1次成功创建订单
- 重复请求返回明确的"已参与秒杀"错误码（如409）
- DB唯一索引冲突时正确处理，不抛异常

---

### Step 7: 数据一致性保证（库存回滚）

**做什么:** 实现订单创建失败时的库存回滚机制，确保最终一致性。

**产出物:**
- `order-service/src/main/java/com/course/ecommerce/service/CompensationService.java` - 补偿服务接口
- `order-service/src/main/java/com/course/ecommerce/service/impl/CompensationServiceImpl.java` - 实现类
- `inventory-service/src/main/java/com/course/ecommerce/controller/InventoryInternalController.java` - 内部库存回滚接口

**验收:**
- 模拟订单创建失败（如DB异常），库存回滚成功
- 使用JMeter压测，最终库存数量正确（DB与Redis一致）
- 无重复扣减或丢失库存情况

---

### Step 8: Docker Compose集成与JMeter压测

**做什么:** 更新 docker-compose.yml 添加Kafka和Zookeeper，编写JMeter压测脚本。

**产出物:**
- `docker-compose.yml` - 修改：添加zookeeper、kafka服务
- `jmeter/seckill-test.jmx` - 秒杀压测脚本
- `docs/superpowers/specs/seckill-test-results.md` - 压测报告模板

**验收:**
- `docker compose up -d` 启动所有服务包括Kafka
- JMeter 1000并发秒杀，系统稳定，无超卖
- 压测后DB库存与预期一致

---

## 非目标

- **不能动的东西:**
  - product-service 的缓存三防护逻辑（穿透/击穿/雪崩）
  - 已有的MySQL读写分离配置和路由逻辑
  - 现有商品详情查询接口
  - 现有ES搜索功能

- **不跨步骤顺手优化:**
  - 不修改非秒杀相关的订单查询接口
  - 不优化库存查询接口（使用现有基础接口）
  - 不改动用户服务认证逻辑

- **新依赖需先确认:**
  - Kafka客户端版本与Spring Boot 3.2.x兼容
  - 雪花算法workerId分配策略（单机固定，集群需协调）

---

## 参考

**风格参考:**
- 现有 product-service 的 `ProductCacheService.java` - Redis操作风格
- 现有 order-service 的 `OrderServiceImpl.java` - Service层结构
- 现有 `docker-compose.yml` - 服务配置风格

**接口参考:**
- 秒杀下单API: `POST /api/orders/seckill`
  - Request: `{ "productId": 1, "quantity": 1, "seckillActivityId": 1001 }`
  - Response: `{ "code": 202, "message": "排队中", "data": { "orderNo": "XXX", "status": "PROCESSING" } }`

**文档链接:**
- `CLAUDE.md` - 项目架构和开发规范
- `docs/superpowers/plans/2026-03-30-next-phase.md` - 上一阶段完成内容

---

## 自动化验收命令

**运行环境:** Docker Compose（本地开发）或 Kubernetes（生产）

**执行命令格式:**
```bash
# 构建服务
docker run --rm -v "$(pwd):/workspace" -w /workspace docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17 mvn package -DskipTests -pl order-service,inventory-service -am

# 启动环境
docker compose up -d --build

# 等待服务就绪
sleep 30 && docker compose ps
```

**Step 1 验收:**
```bash
# 预热库存
curl -X POST http://localhost:8084/api/inventory/seckill/warmup/1/100

# 检查Redis库存
docker exec redis redis-cli -a redis123 GET "seckill:stock:1"
# 期望: "100"

# 并发扣减测试
for i in $(seq 1 10); do curl -X POST "http://localhost:8084/api/inventory/seckill/deduct/1/1" & done; wait
# 期望: 10个请求，前10个返回剩余库存90-81，第11个返回-1（售罄）
```

**Step 2 验收:**
```bash
# 测试雪花ID生成（通过order-service健康检查接口返回）
curl http://localhost:8085/actuator/health
```

**Step 3 验收:**
```bash
# 验证幂等性Key生成
# 通过代码单元测试验证
```

**Step 4 验收:**
```bash
# 发起秒杀请求
curl -X POST http://localhost:8085/api/orders/seckill \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -d '{"productId":1,"quantity":1,"seckillActivityId":1001}'
# 期望: {"code":202,"message":"排队中","data":{"orderNo":"...","status":"PROCESSING"}}

# 重复请求（相同用户）
# 期望: {"code":409,"message":"您已参与该商品秒杀"}
```

**Step 5 验收:**
```bash
# 等待消息消费
sleep 5

# 查询订单是否创建
curl http://localhost:8085/api/orders/{orderNo}
# 期望: 订单存在，状态为CREATED
```

**Step 6 验收:**
```bash
# 并发压测（使用JMeter或脚本）
# 期望: 100并发，只有1个成功创建订单，其余返回409
```

**Step 7 验收:**
```bash
# 查询最终库存
curl http://localhost:8084/api/inventory/1
docker exec redis redis-cli -a redis123 GET "seckill:stock:1"
# 期望: DB和Redis库存一致
```

**Step 8 验收:**
```bash
# JMeter压测
docker run --rm --network distributed_system_course_ecommerce-net \
  -v $(pwd)/jmeter:/jmeter alpine/jmeter:latest \
  -n -t /jmeter/seckill-test.jmx -l /jmeter/results/seckill.jtl

# 分析结果
# 期望: 错误率<0.1%，无超卖
```

---

## 成功条件

- [ ] 所有步骤验收命令通过（exit code 0）
- [ ] diff 范围在 order-service、inventory-service、common-core、sql/init.sql、docker-compose.yml 内
- [ ] JMeter 1000并发压测，系统稳定运行，无超卖
- [ ] 幂等性验证：100并发同一用户请求，只有1单成功

---

## 错误处理约定

- **如某步失败：** 先分析原因，给出修复方案，等确认后再修
- **如连续两次失败：** 停下来，列出可能原因，不要继续盲目重试
- **如遇到环境/依赖问题：** 报告具体报错，不要自行修改环境配置（如Maven settings.xml、Docker daemon配置等）

---

## 阶段2 练习点

**在设计层面发现问题，比在代码层面发现问题成本低 10 倍**

重点检查点：
1. **Lua脚本原子性** - 扣减和防重是否在同一个原子操作内？
2. **幂等性Key设计** - 是否包含活动期间ID，避免跨活动误拦截？
3. **库存回滚时机** - 哪些失败场景需要回滚？消费者异常、DB超时、业务校验失败？
4. **消息顺序性** - 同一用户多次请求是否可能乱序消费？如何处理？
5. **雪崩防护** - 如果Kafka消费堆积，如何避免DB被打爆？

---

## 风险决策日志

| 决策点 | 选择 | 原因 |
|--------|------|------|
| 库存扣减策略 | Redis Lua预扣 + 异步确认 | 高性能，削峰，但需回滚补偿 |
| 幂等性维度 | 活动期间（activityId）单次 | 避免用户跨活动无法参与 |
| 订单ID生成 | 雪花算法 | 分布式唯一，有序，性能高 |
| 消息失败处理 | 死信队列 + 补偿服务 | 保证最终一致性 |
| 超卖防护 | Redis Lua + DB唯一索引双重校验 | Redis挡流量，DB兜底 |

---

*设计文档版本: 2026-04-11*
*阶段: 2 - 流程工程化*
