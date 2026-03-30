# 技术栈选型说明

## 1. 编程语言与框架

### 1.1 编程语言

| 语言 | 版本 | 说明 |
|------|------|------|
| Java | 17 (LTS) | 课程主流语言，生态成熟 |

### 1.2 后端框架

| 框架 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.2.x | 快速构建 RESTful API |
| Spring Web | 内置 | Web 开发 |
| Spring Data Redis | 内置 | Redis 集成 |
| Spring AOP | 内置 | 读写分离路由切面 |

### 1.3 持久层框架

| 框架 | 版本 | 说明 |
|------|------|------|
| MyBatis-Plus | 3.5.x | 简化 MyBatis 开发 |
| MySQL Driver | 8.0.x | MySQL JDBC 驱动 |

---

## 2. 数据存储

### 2.1 关系型数据库

| 组件 | 版本 | 说明 |
|------|------|------|
| MySQL | 8.0 | 主从架构，实现读写分离 |

**主从配置：**
- 主库端口：3306
- 从库端口：3307

### 2.2 缓存

| 组件 | 版本 | 说明 |
|------|------|------|
| Redis | 7.2 | 商品缓存（三重防护：穿透/击穿/雪崩） |

**配置：**
- 端口：6379
- 持久化：AOF + RDB

### 2.3 搜索引擎

| 组件 | 版本 | 说明 |
|------|------|------|
| ElasticSearch | 8.11.0 | 商品全文搜索 |

**配置：**
- 端口：9200
- 单节点模式，安全认证关闭
- 启动时从 MySQL 全量同步

---

## 3. 网关与负载均衡

### 3.1 Nginx

| 组件 | 版本 | 说明 |
|------|------|------|
| Nginx | 1.24 | 反向代理、负载均衡、动静分离 |

**主要功能：**
- 反向代理：将请求转发到后端服务
- 负载均衡：轮询/加权轮询分发到多个实例
- 静态资源服务：HTML/CSS/JS 直接返回

---

## 4. 容器与部署

### 4.1 Docker

| 组件 | 版本 | 说明 |
|------|------|------|
| Docker | 24.x | 容器化 |
| Docker Compose | 2.x | 多容器编排 |

### 4.2 容器列表

| 容器名 | 镜像 | 说明 |
|--------|------|------|
| mysql | mysql:8.0 | 主库 |
| mysql-slave | mysql:8.0 | 从库（只读） |
| redis | redis:7.2 | 缓存 |
| elasticsearch | elasticsearch:8.11.0 | 搜索引擎 |
| nginx | nginx:1.24 | 反向代理 + 负载均衡 |
| user-service | eclipse-temurin:17-jre-alpine | 用户服务 |
| product-service-1 | eclipse-temurin:17-jre-alpine | 商品服务实例1 |
| product-service-2 | eclipse-temurin:17-jre-alpine | 商品服务实例2 |
| inventory-service | eclipse-temurin:17-jre-alpine | 库存服务 |
| order-service | eclipse-temurin:17-jre-alpine | 订单服务 |

---

## 5. 安全

### 5.1 认证

| 组件 | 说明 |
|------|------|
| JWT | Token 认证 |
|jjwt | JWT 生成与解析 |

**依赖：**
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
```

---

## 6. 工具

### 6.1 构建工具

| 工具 | 版本 | 说明 |
|------|------|------|
| Maven | 3.9 | 项目构建 |
| JDK | 17 | 编译运行 |

### 6.2 测试工具

| 工具 | 说明 |
|------|------|
| JMeter | 高并发压测 |
| Postman / Apifox | 接口调试 |

### 6.3 日志

| 组件 | 说明 |
|------|------|
| Logback | 日志框架 |
| SLF4J | 日志门面 |

---

## 7. 完整依赖（pom.xml 核心部分）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.1</version>
    </parent>

    <groupId>com.course</groupId>
    <artifactId>ecommerce-system</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>3.5.5</version>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.12.3</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.12.3</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.12.3</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 8. 项目结构

```
distributed_system_course/
├── common-core/               # 共享模块（无业务逻辑）
│   └── src/main/java/com/course/ecommerce/
│       ├── common/            # Result, BusinessException, GlobalExceptionHandler
│       ├── config/            # JwtUtil
│       ├── entity/            # User, Product, Order, Inventory
│       └── dto/               # RegisterRequest, LoginRequest, LoginResponse
├── user-service/              # 用户注册/登录
│   └── src/main/java/com/course/ecommerce/
│       ├── controller/        # UserController
│       ├── service/impl/      # UserServiceImpl (BCrypt + JWT)
│       ├── mapper/            # UserMapper
│       └── config/            # SecurityConfig
├── product-service/           # 商品服务（核心：缓存 + 搜索 + 读写分离）
│   └── src/main/java/com/course/ecommerce/
│       ├── controller/        # ProductController (含 /search 端点)
│       ├── service/
│       │   ├── ProductCacheService.java       # Redis 三重防护
│       │   └── impl/ProductServiceImpl.java   # 缓存击穿/穿透/雪崩逻辑
│       ├── mapper/            # ProductMapper (@ReadOnly)
│       ├── config/
│       │   ├── MyBatisConfig.java             # 双数据源路由
│       │   ├── DataSourceRouter.java          # AbstractRoutingDataSource
│       │   ├── ReadOnly.java                  # 自定义注解
│       │   ├── ReadOnlyRoutingAspect.java     # AOP 读写路由
│       │   ├── ElasticsearchConfig.java       # ES 客户端
│       │   └── InstanceIdInterceptor.java     # X-Instance-Id
│       └── elasticsearch/
│           ├── ProductDocument.java           # ES 文档模型
│           └── ProductSearchService.java      # 同步 + 搜索
├── inventory-service/          # 库存查询（最小实现）
├── order-service/              # 订单管理（最小实现）
├── sql/init.sql               # 建表 + 5 条 Apple 商品测试数据
├── nginx/
│   ├── nginx.conf             # Upstream + location /api/ + static
│   └── html/                  # index.html, style.css, app.js
├── jmeter/
│   ├── product-detail-test.jmx
│   ├── static-file-test.jmx
│   └── results/
├── docker-compose.yml         # 完整 10 容器编排
├── pom.xml                    # 父 POM（多模块聚合）
└── docs/                      # 架构、API、数据库文档
```

---

## 9. 技术选型总结

| 层次 | 选型 | 理由 |
|------|------|------|
| 语言 | Java 17 | 主流、成熟 |
| 框架 | Spring Boot 3.2 | 快速开发、生态完善 |
| ORM | MyBatis-Plus | 简化开发、SQL可控 |
| 数据库 | MySQL 8.0 | 关系型存储，主从读写分离 |
| 缓存 | Redis 7.2 | 高性能缓存，三重防护 |
| 搜索 | ElasticSearch 8.11.0 | 全文检索 |
| 网关 | Nginx | 反向代理、负载均衡 |
| 认证 | JWT | 无状态、易扩展 |
| 部署 | Docker | 环境一致、快速部署 |
