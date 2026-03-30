# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a distributed systems course project — a simplified e-commerce system with "user-product-inventory-order" business flow. It demonstrates **high-concurrency reads**, **distributed caching with triple protection (penetration/breakdown/avalanche)**, **read-write separation**, **ElasticSearch search**, and **load balancing** through containerized microservices.

The system is delivered as a **multi-module Maven project** with 4 independent Spring Boot services, orchestrated via Docker Compose with Nginx as a gateway.

## Tech Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.2.x
- **ORM**: MyBatis-Plus 3.5.x
- **Database**: MySQL 8.0 (Master-Saster, initialized via `sql/init.sql`)
- **Cache**: Redis 7.2 (product-service, triple protection)
- **Search**: ElasticSearch 8.11.0 (product full-text search)
- **Gateway**: Nginx 1.24 (static files + load balancing + reverse proxy)
- **Auth**: JWT (jjwt 0.12.3)
- **Build**: Maven 3.9
- **Container**: Docker Compose (10 containers)
- **Test**: JMeter 5.6.3

## Architecture

4 microservices + 1 common module:

| Service | Port | Purpose |
|---------|------|---------|
| **user-service** | 8081 | Registration, login, user info |
| **product-service** | 8082 | Product details (Redis cached + ES search + R/W separation) |
| **inventory-service** | 8084 | Stock query |
| **order-service** | 8085 | Order creation and query |

Supporting infrastructure:
- **nginx**:80 — Gateway serving static files and proxying `/api/` to product-service
- **mysql**:3306 — Master database (writes)
- **mysql-slave**:3307 — Slave database (reads, read_only)
- **redis**:6379 — Product cache with triple protection (password: `redis123`)
- **elasticsearch**:9200 — Product search index (single-node, security disabled)

### Key Architecture Decisions

- **Cache triple protection** on product-service:
  - **Penetration**: Cache `"null"` for non-existent keys with short TTL (5 min)
  - **Breakdown**: Logical expiry (`CacheData<T>` wrapper) + SETNX mutex + async refresh thread
  - **Avalanche**: TTL jitter (30 min ± 5 min random offset)
- **Round-robin load balancing** via Nginx upstream of two product-service containers
- **Read-write separation**: `AbstractRoutingDataSource` + `@ReadOnly` AOP routes reads to slave, writes to master
- **No inter-service calls**: services do not call each other; frontend aggregates data client-side
- **No service registry**: Nginx handles service discovery via fixed container hostnames
- **ES sync**: `@PostConstruct` full sync from MySQL to ElasticSearch on startup

## Project Structure

```
distributed_system_course/
├── common-core/                  # Shared code (no business logic)
│   └── src/main/java/com/course/ecommerce/
│       ├── common/Result.java, BusinessException.java, GlobalExceptionHandler.java
│       ├── config/JwtUtil.java
│       ├── entity/User.java
│       └── dto/RegisterRequest, LoginRequest, LoginResponse
├── user-service/                # User registration/login (Spring Security + BCrypt)
│   └── src/main/java/com/course/ecommerce/
│       ├── controller/UserController.java
│       ├── service/impl/UserServiceImpl.java
│       ├── mapper/UserMapper.java
│       └── config/SecurityConfig.java
├── product-service/              # Product read with Redis cache + ES search + R/W separation
│   └── src/main/java/com/course/ecommerce/
│       ├── controller/ProductController.java         # REST endpoints including /search
│       ├── service/impl/ProductServiceImpl.java      # 3-protection cache logic
│       ├── service/ProductCacheService.java          # Redis cache: null value, SETNX lock, logical expiry
│       ├── mapper/ProductMapper.java                 # @ReadOnly annotated
│       ├── config/
│       │   ├── MyBatisConfig.java                    # Dual datasource + AbstractRoutingDataSource
│       │   ├── DataSourceRouter.java                 # Routes master/slave based on ThreadLocal
│       │   ├── DataSourceContextHolder.java          # ThreadLocal context holder
│       │   ├── DataSourceNames.java                  # "master" / "slave" constants
│       │   ├── ReadOnly.java                         # @ReadOnly annotation
│       │   ├── ReadOnlyRoutingAspect.java            # AOP aspect for read routing
│       │   ├── ElasticsearchConfig.java              # ES RestClient + transport
│       │   └── InstanceIdInterceptor.java            # X-Instance-Id header
│       └── elasticsearch/
│           ├── ProductDocument.java                  # ES document model
│           └── ProductSearchService.java             # @PostConstruct sync + search()
├── inventory-service/             # Stock query (minimal implementation)
├── order-service/                 # Order management (minimal implementation)
├── sql/
│   └── init.sql                   # Database schema + 5 Apple products + test data
├── nginx/
│   ├── nginx.conf                 # Upstream, location /api/, static files
│   └── html/index.html, style.css, app.js
├── jmeter/
│   ├── product-detail-test.jmx    # Cache warmup → high concurrency → LB check
│   ├── static-file-test.jmx       # Static resource load test
│   └── results/                   # .jtl output files
├── docker-compose.yml             # Full stack (all 10 services)
├── docker-compose-step4.yml       # Step 4 subset (nginx + product × 2)
└── pom.xml                        # Parent POM, multi-module aggregator
```

## Development Commands

```bash
# Build all modules (creates fat JARs with repackage)
mvn clean package -DskipTests

# Build specific services
mvn package -DskipTests -pl user-service,inventory-service,order-service -am

# Start full environment (all containers)
docker compose up -d --build

# Start Step 4 subset (nginx + product × 2 only)
docker compose -f docker-compose-step4.yml up -d --build

# Wait for healthy and test
sleep 25 && docker compose ps
curl http://localhost/
curl http://localhost/api/products/1
curl "http://localhost/api/products/search?q=iPhone"

# Verify load balancing (need Connection: close)
curl -H "Connection: close" -i http://localhost/api/products/1
curl -H "Connection: close" -i http://localhost/api/products/1

# Run JMeter tests (Docker-based, recommended)
docker run --rm --network distributed_system_course_ecommerce-net \
  -v $(pwd)/jmeter:/jmeter alpine/jmeter:latest \
  -n -t /jmeter/product-detail-test.jmx -l /jmeter/results/product-detail.jtl

# Tear down and clean volumes
docker compose down -v
```

## Port Assignments

| Component | Port | Notes |
|-----------|------|-------|
| nginx | 80 | HTTP gateway |
| user-service | 8081 | |
| product-service-1 | 8082 (mapped to 8082) | Internal port 8082 |
| product-service-2 | 8082 (mapped to 8083) | Internal port 8082 |
| inventory-service | 8084 | |
| order-service | 8085 | |
| mysql (master) | 3306 | |
| mysql-slave | 3307 | Internal port 3306, mapped to 3307 |
| redis | 6379 | Password: redis123 |
| elasticsearch | 9200 | Single-node, security disabled |

## Common Issues

1. **Stale MySQL volume**: If services fail to connect to DB with "Access denied for user 'ecommerce'", run `docker compose down -v` to reset volumes and re-initialize from `init.sql`.

2. **Healthcheck tool mismatch**: `eclipse-temurin:17-jre-alpine` images have `wget` but NOT `curl`. Use `wget -qO-` for Java service healthchecks, `curl -f` for nginx (nginx:1.24 image has curl).

3. **Fat JAR not built**: If a service container fails with "no main manifest attribute", its `pom.xml` is missing `<goal>repackage</goal>` in the spring-boot-maven-plugin configuration.

4. **JMeter not installed**: Use Docker-based JMeter with `--network` flag to reach services. Or download from `https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.6.3.tgz`.

5. **JMeter 502 errors from Docker**: JMeter running in Docker cannot reach `host.docker.internal` on some setups. Run on the same Docker network with `--network distributed_system_course_ecommerce-net` and use service names (e.g., `nginx`) as target host.

6. **MyBatisPlus XML mapper error**: If product-service fails with "class path resource [mapper/] cannot be resolved", ensure `MyBatisConfig` uses `MybatisSqlSessionFactoryBean` (not `SqlSessionFactoryBean`) and does NOT configure `mapperLocations`.

## Step Completion Status

| Step | Description | Status |
|------|-------------|--------|
| Step 1 | Multi-module Maven skeleton | Complete |
| Step 2 | Core service split + minimal interfaces | Complete |
| Step 3 | Redis caching on product-service | Complete |
| Step 4 | Nginx gateway + load balancing | Complete |
| Step 5 | Docker Compose + JMeter testing | Complete |
| Bonus | Cache triple protection + MySQL R/W separation + ES search | Complete |

See `docs/superpowers/` for step-by-step progress reports and debugging notes.
