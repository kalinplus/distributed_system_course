## 任务卡 v2（阶段 2：流程工程化）

### 背景

当前仓库实际代码还是单体 Spring Boot 项目，核心实现主要集中在 `ecommerce-system` 内，已落地的业务以 `user` 模块为主；但课程目标要求展示“分布式系统下的高并发读能力”，并明确要求覆盖：

- 容器化部署
- 多实例负载均衡
- Nginx 动静分离
- Redis 分布式缓存及缓存风险治理

基于课程验收口径，本阶段选择 **Plan B：拆分为多个服务**，而不是继续以单体多副本方式交付。这样更贴近课程对“分布式架构”的预期，也便于答辩时从服务边界、流量入口、缓存层和压测结果四个层面完整说明设计。

### 最终目标

将当前单体项目演进为一个可通过 `docker compose` 启动的多服务系统：包含 `user-service`、`product-service`、`inventory-service`、`order-service`、`mysql`、`redis`、`nginx`，其中 `product-service` 支撑高并发商品详情读请求，Nginx 负责静态资源分发与后端负载均衡，Redis 提供商品详情缓存并处理穿透、击穿、雪崩。

### 分步计划（有序，每步独立可验收）

> **边界原则**：每一步只做该步必须完成的事情，不提前跨越边界。所有技术选型以”课程验收最小闭环”为准。

#### Step 1: 完成项目拆分设计与多模块骨架搭建

- 产出物:
  - 根目录父 `pom.xml`（聚合所有子模块，统一依赖版本）
  - 多模块目录骨架：`common-core`、`user-service`、`product-service`、`inventory-service`、`order-service`
  - 各服务独立 `application.yml` 基础配置与启动类
- 验收:
  - `mvn -q -DskipTests package` 通过
  - 根项目可识别所有子模块
  - 各服务模块可以单独编译通过

##### Step 1 详细规则

**模块结构**：

```
根 pom.xml
├── common-core/        # 公共依赖和通用代码
├── user-service/       # 用户服务（迁移现有代码）
├── product-service/    # 商品服务（骨架）
├── inventory-service/  # 库存服务（骨架）
└── order-service/      # 订单服务（骨架）
```

**common-core 边界定义**：

✅ **允许放入**：

- 通用技术组件：Result 统一返回体、BusinessException 业务异常、GlobalExceptionHandler 全局异常处理
- 通用工具类：JwtUtil 工具类（供 user-service 使用）
- 业务通用实体：User 用户实体、UserDTO 等业务 DTO
- 请求/响应对象：RegisterRequest、LoginRequest、LoginResponse 等

❌ **禁止放入**：

- 具体 Service 实现类（如 UserServiceImpl）
- Controller 类
- Mapper 接口或 XML（业务数据库操作）
- 业务逻辑代码

**各服务骨架要求**：

- 独立 `src/main/java/.../XxxApplication.java` 启动类
- 独立 `src/main/resources/application.yml` 配置
- 独立端口（user-service:8081, product-service:8082, inventory-service:8083, order-service:8084）
- 最小 Controller（返回占位响应或健康检查）

**服务边界说明**：

- `user-service`：承担用户注册/登录能力，**不是**高并发读主场景
- `product-service`：承担商品查询能力，**是**高并发读主场景
- `inventory-service`、`order-service`：本阶段只做最小可运行实现，不求完整

---

#### Step 2: 完成核心业务服务拆分与最小接口闭环

- 产出物:
  - `user-service`：迁移现有用户注册/登录/查询能力
    - **包含测试代码迁移**：将 `ecommerce-system/src/test/` 下的测试迁移到 `user-service/src/test/`
  - `product-service`：新增商品详情接口 `GET /api/products/{id}`
  - `inventory-service`：最小库存查询接口
  - `order-service`：最小订单查询/占位接口
  - 数据表初始化脚本或表结构说明
  - 各服务最小单元、冒烟测试或启动验证脚本
  - **删除旧目录**：Step 2 完成后删除 `ecommerce-system/` 目录
- 验收:
  - `mvn -q -pl user-service,product-service,inventory-service,order-service -am test` 通过
  - `user-service` 迁移后原有用户相关接口语义不变
  - `product-service` 可直接从数据库读取商品详情
  - `inventory-service`、`order-service` 至少具备可启动、可访问、可说明的最小接口
  - 四个服务都能独立启动并返回健康状态或最小业务响应

##### Step 2 详细规则

**user-service 迁移规则**：

- 保持原有包结构（controller、service、mapper、entity 等）
- 保持原有接口语义不变
- 依赖 common-core 中的通用组件
- 配置文件从原有 application.yml 迁移
- **测试代码迁移**：
  - 将 `ecommerce-system/src/test/java/com/course/ecommerce/service/UserServiceTest.java` 迁移到 `user-service/src/test/java/com/course/ecommerce/service/`
  - 将 `ecommerce-system/src/test/java/com/course/ecommerce/controller/UserControllerTest.java` 迁移到 `user-service/src/test/java/com/course/ecommerce/controller/`
  - 将 `ecommerce-system/src/test/java/com/course/ecommerce/integration/UserIntegrationTest.java` 迁移到 `user-service/src/test/java/com/course/ecommerce/integration/`
  - 测试类中的 import 路径需要调整为新模块路径
- **删除旧目录**：Step 2 完成后删除 `ecommerce-system/` 目录

**product-service 最小实现**：

- Product 实体（对应 products 表）
- ProductMapper（MyBatis-Plus）
- ProductService、ProductServiceImpl
- ProductController：提供 `GET /api/products/{id}` 接口
- 返回 JSON 格式的商品详情

**inventory-service 最小实现**：

- Inventory 实体（对应 inventory 表）
- InventoryMapper
- InventoryController：提供占位接口 `GET /api/inventory/{productId}`
- 返回格式：`{“productId”: 1, “stock”: 100}`

**order-service 最小实现**：

- Order 实体
- OrderMapper
- OrderController：提供占位接口 `GET /api/orders/{id}`
- 返回格式：`{“id”: 1, “status”: “PENDING”}`

**数据库规则**：

- 优先使用单 MySQL 容器
- 主从复制作为后续加分项，非主路径
- 各服务可共用一个数据库实例，通过表名区分
- 提供 init.sql 初始化表结构

**代码边界**：

- 各服务**不进行**服务间调用（Feign/RestTemplate）
- 各服务**不依赖**其他服务的数据库
- 认证/JWT 保留在 user-service，common-core 只放 JwtUtil 工具类

#### Step 3: 在 `product-service` 引入 Redis 缓存并完成缓存风险治理

- 产出物:
  - 商品详情缓存读写链路
  - 缓存 key 设计与 TTL 策略
  - 缓存穿透处理：空值缓存 / 参数校验
  - 缓存击穿处理：热点 key 重建互斥策略或逻辑过期策略
  - 缓存雪崩处理：TTL 随机抖动 / 分散过期时间
  - 缓存行为说明文档与对应测试用例
- 验收:
  - `mvn -q -pl product-service -am test` 通过
  - 首次查询商品详情时走 DB，后续重复请求命中 Redis
  - 查询不存在商品时不会持续打穿数据库
  - 热点商品缓存失效时不会出现并发下大量请求同时回源
  - 缓存过期策略可被配置和说明，具备雪崩防护依据

##### Step 3 详细规则

**边界限定**：

- 只在 `product-service` 引入 Redis
- 不修改 user-service、inventory-service、order-service
- 不引入其他缓存方案（Ehcache、Caffeine 等）

**Redis 配置**：

- 使用 Redis 单节点（不引入集群）
- 配置在 application.yml 中
- 序列化方式：JSON

**缓存实现要点**：

- Cache-Aside 模式
- 缓存 key 格式：`product:detail:{id}`
- TTL：默认 30 分钟，带随机抖动

**风险治理**：

- 穿透：空值缓存（key 存在但值为 "null"，短 TTL）
- 击穿：互斥锁或逻辑过期（推荐逻辑过期）
- 雪崩：TTL 随机偏移量（±5 分钟）

#### Step 4: 完成 Nginx 网关、动静分离与后端负载均衡

- 产出物:
  - `nginx.conf`
  - 静态页面资源：`index.html`、`style.css`、`app.js`
  - `/api/` 代理配置
  - `product-service` 双实例 upstream 配置
  - 至少两种负载均衡策略配置方案（如 `round robin`、`least_conn` 或 `ip_hash`）
  - 后端实例标识日志方案（如实例名、端口、响应头）
- 验收:
  - `docker compose up -d nginx product-service-1 product-service-2` 成功
  - `curl http://localhost/` 返回静态页面
  - `curl http://localhost/api/products/1` 返回商品详情
  - 从日志或响应头可观察到请求被分发到不同实例

##### Step 4 详细规则

**边界限定**：

- 只配置 Nginx 静态资源和负载均衡
- 不引入 API Gateway（如 Spring Cloud Gateway）
- 不配置熔断、限流等功能

**Nginx 配置要求**：

- 静态资源目录：`/usr/share/nginx/html/`
- 动态代理：`location /api/` 转发到后端
- upstream 命名统一：`product-service`（为后续 docker compose 铺路）

**负载均衡策略**：

- 默认：round robin
- 备选：least_conn、ip_hash（至少配置两种）

**实例标识**：

- product-service 实例添加响应头：`X-Instance-Id`
- 或在日志中输出实例端口

#### Step 5: 完成容器编排与压测验收

- 产出物:
  - 各服务 `Dockerfile`
  - 根级 `docker-compose.yml`
  - `mysql`、`redis`、`nginx`、4 个业务服务的容器启动配置
  - JMeter 测试计划：
    - 静态资源压测
    - 商品详情 API 压测
    - 负载均衡验证压测
  - 压测结果记录文档（响应时间、吞吐、实例分布）
- 验收:
  - `docker compose up -d --build` 可一次性启动完整环境
  - `nginx`、`redis`、`mysql`、各业务服务容器状态正常
  - `jmeter -n -t jmeter/product-detail-test.jmx -l jmeter/results/product-detail.jtl` 执行成功
  - `jmeter -n -t jmeter/static-file-test.jmx -l jmeter/results/static-file.jtl` 执行成功
  - 压测后能拿出证据说明：
    - 静态资源响应快于经后端处理的动态请求
    - 请求在多个 `product-service` 实例之间分布大致均衡
    - Redis 缓存命中后响应时间下降

##### Step 5 详细规则

**边界限定**：

- 使用 docker compose 而非 Kubernetes
- MySQL 使用单节点（主从作为加分项）
- 不引入服务注册中心（如 Nacos）

**Docker Compose 结构**：

```yaml
services:
  mysql:
    image: mysql:8.0
  redis:
    image: redis:7.2
  nginx:
    image: nginx:1.24
  user-service:
    build: ./user-service
  product-service:
    # 两个实例
  inventory-service:
  order-service:
```

**JMeter 测试要求**：

- product-detail-test.jmx：模拟高并发商品查询
- static-file-test.jmx：模拟静态资源请求
- 结果保存到 `jmeter/results/` 目录

**压测证据要求**：

- 响应时间对比（静态 vs 动态）
- 实例分布统计
- 缓存命中前后响应时间对比

> 每步完成后暂停，等我确认再进入下一步
> 阶段 2 最重要的练习点：**在设计层面发现问题，比在代码层面发现问题成本低 10 倍**

### 非目标

- 不在本阶段引入注册中心、配置中心、消息队列、分布式事务等超出课程核心目标的基础设施
- 不把所有业务都做成完整电商系统，只做课程验收需要的最小闭环
- 不跨步骤顺手优化无关代码
- 不重写现有认证方案，除非拆分后证明当前方案无法继续使用
- 不引入新依赖；若 Redis、测试或锁实现必须新增依赖，需先确认

### 通用边界与规则

#### 各步骤边界原则

1. **不跨步**：每步只完成该步产出物，不提前实现后续步骤功能
2. **不引入额外基础设施**：无注册中心、无配置中心、无消息队列
3. **依赖管理**：根 pom 统一版本，各模块继承
4. **服务独立**：各服务可独立编译、启动、测试

#### common-core 边界规则

- ✅ 技术通用件：Result、BusinessException、GlobalExceptionHandler
- ✅ 工具类：JwtUtil
- ✅ 业务通用件：User 实体、UserDTO、RegisterRequest 等
- ❌ 业务逻辑：Service 实现、Controller、Mapper

#### 数据库边界规则

- Step 2：单 MySQL 容器，init.sql 初始化
- Step 5：主从作为加分项，不强制
- 各服务通过表名区分，不跨库 join

#### 服务通信边界规则

- Step 1-3：**不进行**服务间调用
- Step 4-5：Nginx 负载均衡，不使用 Feign/RestTemplate

#### 认证边界规则

- JwtUtil 放在 common-core
- Token 验证逻辑保留在各服务（如需要）
- 不使用 Spring Security OAuth2

#### Docker 边界规则

- 各服务独立 Dockerfile
- 使用 docker compose 编排（不适用 Kubernetes）
- 端口统一规划：
  - user-service: 8081
  - product-service: 8082
  - inventory-service: 8083
  - order-service: 8084
  - mysql: 3306
  - redis: 6379
  - nginx: 80

### 参考

- 仓库现有文档：
  - `docs/architecture.md`
  - `docs/api.md`
  - `docs/database.md`
  - `docs/startup-guide.md`
  - 本文档
- 现有代码基础：
  - `ecommerce-system/src/main/java/com/course/ecommerce/...`
  - `ecommerce-system/src/main/resources/application.yml`
  - `docker/docker-compose.yml`
- 基础设施配置参考：
  - Nginx upstream / location 代理配置
  - Spring Boot 多模块 Maven 组织方式
  - Redis cache-aside 模式
  - JMeter HTTP 压测计划组织方式

### 自动化验收命令

- 运行环境: 本项目不使用 conda，使用 `Java 17 + Maven + Docker Compose + JMeter`
- 执行命令格式:
  - 编译/测试：`mvn ...`
  - 容器编排：`docker compose ...`
  - 压测：`jmeter ...`

[每步完成后可直接运行以下命令验收：]

[Step1:]

```bash
mvn -q -DskipTests package
```

[Step2:]

```bash
mvn -q -pl user-service,product-service,inventory-service,order-service -am test
```

[Step3:]

```bash
mvn -q -pl product-service -am test
```

[Step4:]

```bash
docker compose up -d nginx product-service-1 product-service-2
curl http://localhost/
curl http://localhost/api/products/1
```

[Step5:]

```bash
docker compose up -d --build
jmeter -n -t jmeter/product-detail-test.jmx -l jmeter/results/product-detail.jtl
jmeter -n -t jmeter/static-file-test.jmx -l jmeter/results/static-file.jtl
```

### 成功条件

- 所有步骤验收命令通过（exit code 0）
- diff 范围控制在预期模块内：
  - 多模块结构
  - 服务模块代码
  - Docker / Nginx / JMeter / 文档
- 原有用户相关能力在拆分后保持可用
- 商品详情接口在高并发读场景下具备可展示的缓存收益和多实例分流效果

### 错误处理约定

- 如某步失败：先分析原因，给出修复方案，等确认后再修
- 如连续两次失败：停下来，列出可能原因，不要继续盲目重试
- 如遇到环境/依赖问题：报告具体报错，不要自行修改环境配置

---

在开始实施之前，请先：

1. 用你自己的话复述：目标是什么、边界是什么
2. 列出你认为的风险点或歧义
3. 给出最小改动方案（只写思路，不写代码）
4. 等我确认后再实施
5. 针对上面我不懂的部分，给出补充说明、你的参考和预期等
