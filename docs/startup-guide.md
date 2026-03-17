# 项目启动指南

## 前提条件

启动项目前，确保以下软件已安装并运行：
- **Docker Desktop**（必须处于运行状态，任务栏有鲸鱼图标）
- **Java 17**
- **Maven**

---

## 第一步：启动基础服务（MySQL + Redis）

在项目根目录下执行：

```bash
docker compose -f docker/docker-compose.yml up -d
```

这一条命令会同时启动 3 个服务：

| 服务 | 作用 | 端口 |
|------|------|------|
| `mysql-master` | MySQL 主库，负责写操作 | 3306 |
| `mysql-slave` | MySQL 从库，负责读操作 | 3307 |
| `redis` | 缓存，加速高频读取 | 6379 |

启动后可用以下命令确认服务正在运行：

```bash
docker ps
```

看到三行记录（STATUS 为 Up）说明启动成功。

---

## 第二步：启动应用

```bash
# 先编译打包
mvn clean package -DskipTests

# 再运行
java -jar target/ecommerce-system-1.0.0.jar
```

应用启动后访问 `http://localhost:8080`。

---

## 停止服务

```bash
# 停止 MySQL 和 Redis（数据不会丢失）
docker compose -f docker/docker-compose.yml down

# 停止应用：在运行应用的终端按 Ctrl + C
```

---

## 连接信息（供数据库工具使用）

| 服务 | 地址 | 用户名 | 密码 |
|------|------|--------|------|
| MySQL 主库 | localhost:3306 | ecommerce | ecommerce123 |
| MySQL 从库 | localhost:3307 | ecommerce | ecommerce123 |
| Redis | localhost:6379 | — | redis123 |

数据库名：`ecommerce`
