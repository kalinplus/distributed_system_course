# 项目启动指南

## 前提条件

启动项目前，确保以下软件已安装并运行：
- **Docker Desktop**（必须处于运行状态）
- **Java 17**
- **Maven 3.9+**

---

## 第一步：编译打包

在项目根目录下执行：

```bash
mvn clean package -DskipTests
```

编译成功后会看到各模块的 BUILD SUCCESS。

---

## 第二步：一键启动所有服务

```bash
docker compose up -d --build
```

这一条命令会同时启动 **10 个容器**：

| 服务 | 作用 | 端口 |
|------|------|------|
| `nginx` | 网关（反向代理 + 静态文件 + 负载均衡） | 80 |
| `user-service` | 用户注册/登录 | 8081 |
| `product-service-1` | 商品服务实例1（Redis缓存 + ES搜索） | 8082 |
| `product-service-2` | 商品服务实例2（Redis缓存 + ES搜索） | 8083 |
| `inventory-service` | 库存查询 | 8084 |
| `order-service` | 订单管理 | 8085 |
| `mysql` | MySQL 主库（读写） | 3306 |
| `mysql-slave` | MySQL 从库（只读） | 3307 |
| `redis` | 缓存 | 6379 |
| `elasticsearch` | 商品搜索引擎 | 9200 |

等待约 25 秒后确认服务健康：

```bash
docker compose ps
# 所有服务应显示 "healthy"
```

---

## 第三步：验证功能

```bash
# 首页
curl http://localhost/

# 商品详情（Redis 缓存）
curl http://localhost/api/products/1

# 商品搜索（ElasticSearch）
curl "http://localhost/api/products/search?q=iPhone"

# 缓存穿透测试（不存在的商品）
curl http://localhost/api/products/999999

# 负载均衡验证（观察 X-Instance-Id 响应头）
curl -H "Connection: close" -i http://localhost/api/products/1
curl -H "Connection: close" -i http://localhost/api/products/1
```

---

## 第四步：运行压测（可选）

```bash
# 使用 Docker JMeter
docker run --rm --network distributed_system_course_ecommerce-net \
  -v $(pwd)/jmeter:/jmeter alpine/jmeter:latest \
  -n -t /jmeter/product-detail-test.jmx -l /jmeter/results/product-detail.jtl

docker run --rm --network distributed_system_course_ecommerce-net \
  -v $(pwd)/jmeter:/jmeter alpine/jmeter:latest \
  -n -t /jmeter/static-file-test.jmx -l /jmeter/results/static-file.jtl
```

---

## 停止服务

```bash
# 停止并删除容器（数据卷保留）
docker compose down

# 停止并删除容器和数据卷（完全重置）
docker compose down -v
```

---

## 连接信息（供数据库工具使用）

| 服务 | 地址 | 用户名 | 密码 |
|------|------|--------|------|
| MySQL 主库 | localhost:3306 | ecommerce | ecommerce123 |
| MySQL 从库 | localhost:3307 | ecommerce | ecommerce123 |
| Redis | localhost:6379 | — | redis123 |
| ElasticSearch | localhost:9200 | — | — |

数据库名：`ecommerce`
