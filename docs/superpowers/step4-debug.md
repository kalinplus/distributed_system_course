# Step 4 问题排查文档

## 验收要求

根据 `2026-03-18-plan-b-task-card.md`，Step 4 需要：

### 验收命令
```bash
docker compose up -d nginx product-service-1 product-service-2
curl http://localhost/
curl http://localhost/api/products/1
```

### 验收标准
1. `curl http://localhost/` 返回静态页面
2. `curl http://localhost/api/products/1` 返回商品详情
3. 从日志或响应头可观察到请求被分发到不同实例

---

## 当前状态

### 已完成的产出物
- [x] `nginx/nginx.conf` - 静态资源 + 负载均衡配置
- [x] `nginx/html/index.html`, `style.css`, `app.js` - 静态页面
- [x] `product-service` 添加 `X-Instance-Id` 响应头拦截器
- [x] `docker-compose-step4.yml` - 编排配置

### 遇到的问题

#### 问题 1: JAR 包无法运行
- **现象**: `no main manifest attribute, in app.jar`
- **原因**: pom.xml 缺少 spring-boot-maven-plugin 的 repackage 配置
- **解决**: 添加 executions 配置

#### 问题 2: Docker 镜像拉取失败
- **现象**: `failed to do request: EOF`
- **原因**: Docker Hub 网络不稳定
- **解决**: 预先 pull 镜像

#### 问题 3: JWT 配置缺失
- **现象**: `Could not resolve placeholder 'jwt.secret'`
- **原因**: common-core 的 JwtUtil 需要 jwt.secret 和 jwt.expiration 配置
- **解决**: 在 application-prod.yml 添加 JWT 配置

#### 问题 4: 反射参数问题
- **现象**: `parameter name information not available via reflection`
- **原因**: Spring 3.x 默认不保留参数名，需要 `-parameters` 编译器参数
- **解决**: 在根 pom.xml 添加 `maven.compiler.parameters=true`

#### 问题 5: Redis 连接失败 (进行中)
- **现象**: 异常消息为 null，前端显示 "服务器内部错误：null"
- **原因**: application-prod.yml 中 Redis 密码未设置默认值
- **解决**: 设置默认密码 `redis123`

#### 问题 6: MySQL 用户权限 (当前阻塞)
- **现象**: `Access denied for user 'ecommerce'@'172.20.0.5' (using password: YES)`
- **原因**: init.sql 未创建 ecommerce 用户并授权
- **解决**: 在 init.sql 添加用户创建和授权语句

---

## 修复记录

### 已修复
1. product-service/pom.xml - 添加 repackage
2. pom.xml - 添加 maven.compiler.parameters=true
3. product-service/src/main/resources/application-prod.yml - 添加 JWT 配置、Redis 默认密码
4. sql/init.sql - 添加用户创建和授权

### 待完成
1. 重启 MySQL 容器以应用新 init.sql
2. 重新构建并启动 product-service
3. 运行验收命令

---

## 快速修复命令

```bash
# 1. 重启 MySQL (需要先停止所有容器)
cd G:/github/distributed_system_course
docker compose -f docker-compose-step4.yml down
docker compose -f docker-compose-step4.yml up -d mysql redis

# 2. 等待 MySQL 就绪后，启动 product-service
docker compose -f docker-compose-step4.yml up -d --build product-service-1 product-service-2

# 3. 验收
curl http://localhost/
curl http://localhost/api/products/1
curl -I http://localhost/api/products/1  # 检查 X-Instance-Id 头
```
