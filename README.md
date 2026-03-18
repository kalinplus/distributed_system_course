# Distributed E-commerce System

A distributed systems course project demonstrating high-concurrency read patterns in a microservice architecture. Built with Spring Boot, Docker Compose, Redis caching, and Nginx load balancing.

## Architecture Overview

```
                          ┌─────────────┐
  Browser ──────────────►│   Nginx     │──► Static files (index.html, app.js, style.css)
                          │   :80       │
                          └──────┬──────┘
                                 │ /api/
                    ┌────────────┴────────────┐
                    │  Upstream (Round Robin) │
                    │  product-service-1      │
                    │  product-service-2      │
                    └────────────┬────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │                         │
              ┌─────▼─────┐            ┌─────▼─────┐
              │   Redis    │            │   MySQL   │
              │  :6379     │            │  :3306    │
              └───────────┘            └───────────┘
```

## Services

| Service | Port | Description |
|---------|------|-------------|
| **nginx** | 80 | Gateway: static file serving + API reverse proxy + load balancing |
| **user-service** | 8081 | User registration and login (JWT-based) |
| **product-service** | 8082 × 2 | Product details with Redis cache (hot data) |
| **inventory-service** | 8084 | Stock query |
| **order-service** | 8085 | Order management |
| **mysql** | 3306 | Shared database (`ecommerce` schema) |
| **redis** | 6379 | Product cache (password: `redis123`) |

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.9+
- Docker Desktop (with Docker Compose)

### 1. Build

```bash
mvn clean package -DskipTests
```

### 2. Start All Services

```bash
docker compose up -d --build
```

Wait ~20 seconds for health checks to pass, then verify:

```bash
docker compose ps
# All 7 services should show "healthy"
```

### 3. Test Endpoints

```bash
# Homepage (static)
curl http://localhost/

# Product API (dynamic, load-balanced)
curl http://localhost/api/products/1

# Verify load balancing — should alternate between product-service-1 and product-service-2
curl -H "Connection: close" -i http://localhost/api/products/1
curl -H "Connection: close" -i http://localhost/api/products/1
```

### 4. Run JMeter Load Tests

```bash
# Download JMeter if not installed
curl -L -o apache-jmeter.tar.gz "https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.6.3.tgz"
tar -xzf apache-jmeter.tar.gz

# Product detail load test (125 requests: warmup + high concurrency + LB check)
./apache-jmeter-5.6.3/bin/jmeter.sh -n -t jmeter/product-detail-test.jmx -l jmeter/results/product-detail.jtl

# Static file load test (600 requests)
./apache-jmeter-5.6.3/bin/jmeter.sh -n -t jmeter/static-file-test.jmx -l jmeter/results/static-file.jtl
```

## Load Test Results

Measured on 2026-03-18:

| Test | Requests | Avg | Min | Max | Error Rate |
|------|----------|-----|-----|-----|------------|
| Product Detail API | 125 | 21 ms | 6 ms | 202 ms | 0% |
| Static Files | 600 | 6 ms | 2 ms | 46 ms | 0% |

Static files are ~3.5× faster than cached API responses, confirming Nginx static file caching effectiveness.

**Load Balancing**: 10 sequential requests distributed as 6→product-service-1, 4→product-service-2 (round-robin).

## Key Features Demonstrated

### 1. Redis Cache-Aside with Risk Governance

Located in `product-service/src/main/java/com/course/ecommerce/service/ProductCacheService.java`:

- **Cache-Aside pattern**: read from cache → miss → load from DB → populate cache
- **TTL with jitter**: 30 minutes ± 5 minutes (avalanche protection)
- **Hot data persistence**: popular products remain cached across requests

### 2. Nginx Load Balancing

Located in `nginx/nginx.conf`:

- Two strategies: `round robin` (default) and `least_conn` (comment-switchable)
- `X-Instance-Id` response header identifies which backend served the request
- Static files served directly from filesystem with `1d` cache headers

### 3. Instance Identification

Located in `product-service/src/main/java/com/course/ecommerce/config/InstanceIdInterceptor.java`:

- Each product-service container exposes its identity via `X-Instance-Id` response header
- Configured via `APP_INSTANCE_ID` environment variable in Docker Compose

### 4. Docker Healthchecks

All containers define `healthcheck` in `docker-compose.yml`:
- **nginx**: `curl -f http://localhost/health` (nginx:1.24 image has curl)
- **Java services**: `wget -qO- http://localhost:{port}/api/{service}/health` (alpine images have wget)
- **mysql**: `mysqladmin ping`
- **redis**: `redis-cli -a redis123 ping`

## Project Structure

```
distributed_system_course/
├── common-core/           # Shared: Result, BusinessException, JwtUtil, User entity, DTOs
├── user-service/          # Registration, login, BCrypt password hashing
├── product-service/       # Product API + Redis caching + X-Instance-Id header
├── inventory-service/     # Stock query (minimal)
├── order-service/         # Order CRUD (minimal)
├── sql/init.sql           # Schema + 5 products + test data
├── nginx/
│   ├── nginx.conf         # Upstream + location /api/ + static files
│   └── html/              # index.html, style.css, app.js
├── jmeter/
│   ├── product-detail-test.jmx   # 3-phase: warmup → concurrency → LB check
│   ├── static-file-test.jmx       # index.html, style.css, app.js load test
│   └── results/           # .jtl output files (git-ignored)
├── docker-compose.yml      # Full stack (all 7 services)
├── docker-compose-step4.yml # Subset: nginx + product × 2 (for Step 4 verification)
├── pom.xml               # Parent POM (multi-module aggregator)
└── CLAUDE.md             # Claude Code guidance
```

## API Endpoints

| Method | Path | Service | Description |
|--------|------|---------|-------------|
| GET | `/` | nginx | Homepage (static HTML) |
| GET | `/api/products/{id}` | product-service | Product detail (Redis cached) |
| GET | `/api/products/health` | product-service | Health check |
| GET | `/api/users/health` | user-service | Health check |
| GET | `/api/inventory/{productId}` | inventory-service | Stock query |
| GET | `/api/inventory/health` | inventory-service | Health check |
| GET | `/api/orders/{id}` | order-service | Order query |
| GET | `/api/orders/health` | order-service | Health check |
| POST | `/api/user/register` | user-service | User registration |
| POST | `/api/user/login` | user-service | User login (returns JWT) |

## Database Schema

See `sql/init.sql` for full DDL:

- `t_user` — username, password_hash, phone, email, status
- `t_product` — name, description, price, stock, category, image_url, status
- `t_inventory` — product_id, stock, reserved_stock
- `t_order` — order_no, user_id, product_id, quantity, total_amount, status

Test data includes 5 Apple products and 1 test user.

## Troubleshooting

### Services won't start after previous run
```bash
docker compose down -v   # Remove volumes to reset DB state
docker compose up -d --build
```

### Product API returns empty / errors
Check MySQL initialized correctly:
```bash
docker exec mysql mysql -uroot -proot123 -e "USE ecommerce; SELECT * FROM t_product;"
```

### Load balancing not visible
Use `Connection: close` header to prevent connection reuse:
```bash
curl -H "Connection: close" -i http://localhost/api/products/1
```

### JMeter XML errors
Use the provided `.jmx` files exactly as-is. JMeter 5.x has strict XML format requirements for the hashTree structure.

## Step Completion

All 5 steps from the course plan are complete:

- ✅ **Step 1**: Multi-module Maven skeleton
- ✅ **Step 2**: Core service split + minimal interfaces
- ✅ **Step 3**: Redis caching on product-service with risk governance
- ✅ **Step 4**: Nginx gateway + dual product-service + load balancing
- ✅ **Step 5**: Docker Compose orchestration + JMeter load testing

See `docs/superpowers/step*-*.md` for detailed progress reports and debugging notes.
