# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a distributed systems course project — a simplified e-commerce system with "user-product-inventory-order" business flow. It demonstrates **high-concurrency reads**, **distributed caching**, **read-write separation**, and **load balancing** through containerized microservices.

The system is delivered as a **multi-module Maven project** with 4 independent Spring Boot services, orchestrated via Docker Compose with Nginx as a gateway.

## Tech Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.2.x
- **ORM**: MyBatis-Plus 3.5.x
- **Database**: MySQL 8.0 (single instance, initialized via `sql/init.sql`)
- **Cache**: Redis 7.2 (product-service only)
- **Gateway**: Nginx 1.24 (static files + load balancing + reverse proxy)
- **Auth**: JWT (jjwt 0.12.3)
- **Build**: Maven 3.9
- **Container**: Docker Compose
- **Test**: JMeter 5.6.3

## Architecture

4 microservices + 1 common module:

| Service | Port | Purpose |
|---------|------|---------|
| **user-service** | 8081 | Registration, login, user info |
| **product-service** | 8082 | Product details (high-concurrency read, Redis cached) |
| **inventory-service** | 8084 | Stock query |
| **order-service** | 8085 | Order creation and query |

Supporting infrastructure:
- **nginx**:80 — Gateway serving static files and proxying `/api/` to product-service
- **mysql**:3306 — Shared database with `ecommerce` schema
- **redis**:6379 — Product cache (password: `redis123`)

### Key Architecture Decisions

- **Cache-Aside pattern** on product-service: read-through Redis cache with 30-min TTL + random jitter (avalanche protection)
- **Round-robin load balancing** via Nginx upstream of two product-service containers
- **No inter-service calls**: services do not call each other; frontend aggregates data client-side
- **No service registry**: Nginx handles service discovery via fixed container hostnames

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
├── product-service/              # Product read with Redis cache
│   └── src/main/java/com/course/ecommerce/
│       ├── controller/ProductController.java
│       ├── service/impl/ProductServiceImpl.java
│       ├── service/ProductCacheService.java   # Redis cache logic
│       ├── mapper/ProductMapper.java
│       └── config/InstanceIdInterceptor.java  # X-Instance-Id header
├── inventory-service/             # Stock query (minimal implementation)
├── order-service/                 # Order management (minimal implementation)
├── sql/
│   └── init.sql                   # Database schema + test data
├── nginx/
│   ├── nginx.conf                 # Upstream, location /api/, static files
│   └── html/index.html, style.css, app.js
├── jmeter/
│   ├── product-detail-test.jmx    # Cache warmup → high concurrency → LB check
│   ├── static-file-test.jmx       # Static resource load test
│   └── results/                   # .jtl output files
├── docker-compose.yml             # Full stack (all services)
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
sleep 20 && docker compose ps
curl http://localhost/
curl http://localhost/api/products/1

# Verify load balancing (need Connection: close)
curl -H "Connection: close" -i http://localhost/api/products/1
curl -H "Connection: close" -i http://localhost/api/products/1

# Run JMeter tests (requires JMeter 5.6.3 installed)
jmeter -n -t jmeter/product-detail-test.jmx -l jmeter/results/product-detail.jtl
jmeter -n -t jmeter/static-file-test.jmx -l jmeter/results/static-file.jtl

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
| mysql | 3306 | |
| redis | 6379 | Password: redis123 |

## Common Issues

1. **Stale MySQL volume**: If services fail to connect to DB with "Access denied for user 'ecommerce'", run `docker compose down -v` to reset volumes and re-initialize from `init.sql`.

2. **Healthcheck tool mismatch**: `eclipse-temurin:17-jre-alpine` images have `wget` but NOT `curl`. Use `wget -qO-` for Java service healthchecks, `curl -f` for nginx (nginx:1.24 image has curl).

3. **Fat JAR not built**: If a service container fails with "no main manifest attribute", its `pom.xml` is missing `<goal>repackage</goal>` in the spring-boot-maven-plugin configuration.

4. **JMeter not installed**: Download from `https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.6.3.tgz` and use `bin/jmeter.sh` (Linux/macOS) or `bin/jmeter.bat` (Windows).

## Step Completion Status

| Step | Description | Status |
|------|-------------|--------|
| Step 1 | Multi-module Maven skeleton | ✅ Complete |
| Step 2 | Core service split + minimal interfaces | ✅ Complete |
| Step 3 | Redis caching on product-service | ✅ Complete |
| Step 4 | Nginx gateway + load balancing | ✅ Complete |
| Step 5 | Docker Compose + JMeter testing | ✅ Complete |

See `docs/superpowers/` for step-by-step progress reports and debugging notes.
