# Distributed E-commerce System

A distributed systems course project demonstrating high-concurrency read patterns in a microservice architecture. Built with Spring Boot, Docker Compose, Redis caching (with penetration/breakdown/avalanche protection), MySQL read-write separation, ElasticSearch search, and Nginx load balancing.

## Architecture Overview

```
                          ┌─────────────┐
  Browser ──────────────►│   Nginx     │──► Static files (index.html, app.js, style.css)
                          │   :80       │
                          └──────┬──────┘
                                 │ /api/
                    ┌────────────┴────────────┐
                    │  Upstream (Round Robin)  │
                    │  product-service-1       │
                    │  product-service-2       │
                    └────────────┬────────────┘
                                 │
            ┌────────────────────┼────────────────────┐
            │                    │                     │
      ┌─────▼─────┐      ┌─────▼──────┐       ┌─────▼──────┐
      │   Redis    │      │ MySQL M/S  │       │   Elastic  │
      │  :6379     │      │ :3306/:3307│       │ Search     │
      │            │      │ Master/Slave│      │  :9200     │
      └───────────┘      └────────────┘       └────────────┘
```

## Services

| Service | Port | Description |
|---------|------|-------------|
| **nginx** | 80 | Gateway: static files + API reverse proxy + load balancing |
| **user-service** | 8081 | User registration and login (JWT-based) |
| **product-service** | 8082 × 2 | Product details with Redis cache + ES search + read-write separation |
| **inventory-service** | 8084 | Stock query |
| **order-service** | 8085 | Order management |
| **mysql** | 3306 | Master database (`ecommerce` schema, writes) |
| **mysql-slave** | 3307 | Slave database (reads, read_only) |
| **redis** | 6379 | Product cache with 3-layer protection (password: `redis123`) |
| **elasticsearch** | 9200 | Product full-text search (single-node, security disabled) |

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

Wait ~25 seconds for health checks to pass, then verify:

```bash
docker compose ps
# All 10 services should show "healthy"
```

### 3. Test Endpoints

```bash
# Homepage (static)
curl http://localhost/

# Product API (dynamic, load-balanced, Redis cached)
curl http://localhost/api/products/1

# ElasticSearch full-text search
curl "http://localhost/api/products/search?q=iPhone"

# Cache penetration test (non-existent product, cached as null)
curl http://localhost/api/products/999999

# Verify load balancing — should alternate between product-service-1 and product-service-2
curl -H "Connection: close" -i http://localhost/api/products/1
curl -H "Connection: close" -i http://localhost/api/products/1
```

### 4. Run JMeter Load Tests

```bash
# Using Docker-based JMeter (recommended)
docker run --rm --network distributed_system_course_ecommerce-net \
  -v $(pwd)/jmeter:/jmeter alpine/jmeter:latest \
  -n -t /jmeter/product-detail-test.jmx -l /jmeter/results/product-detail.jtl

docker run --rm --network distributed_system_course_ecommerce-net \
  -v $(pwd)/jmeter:/jmeter alpine/jmeter:latest \
  -n -t /jmeter/static-file-test.jmx -l /jmeter/results/static-file.jtl

# Or with local JMeter 5.6.3
./apache-jmeter-5.6.3/bin/jmeter.sh -n -t jmeter/product-detail-test.jmx -l jmeter/results/product-detail.jtl
./apache-jmeter-5.6.3/bin/jmeter.sh -n -t jmeter/static-file-test.jmx -l jmeter/results/static-file.jtl
```

## Load Test Results

Measured on 2026-03-30 (after cache protection + read-write separation + ES):

| Test | Requests | Avg | Min | P50 | P95 | P99 | Max | Error Rate |
|------|----------|-----|-----|-----|-----|-----|-----|------------|
| Product Detail API | 125 | 6.6 ms | 3 ms | 5 ms | 13 ms | 38 ms | 38 ms | 0% |
| Static Files | 600 | 2.0 ms | 1 ms | 2 ms | 3 ms | 4 ms | 29 ms | 0% |

- Product API TPS: 31.2 req/s (limited by test thread count, not system capacity)
- Static File TPS: 300.0 req/s
- Static files ~3.3x faster than cached API responses, confirming Nginx static caching effectiveness

**Load Balancing**: 10 sequential requests distributed as product-service-1: 5, product-service-2: 5 (1:1 round-robin).

## Key Features Demonstrated

### 1. Redis Cache with Triple Protection

Located in `product-service/src/main/java/com/course/ecommerce/service/`:

- **Penetration protection**: Non-existent products cache a `"null"` value with short TTL (5 min), preventing repeated DB hits
- **Breakdown protection**: Logical expiry + SETNX distributed mutex + async refresh thread — only one thread rebuilds cache while others return stale data
- **Avalanche protection**: TTL random jitter (30 min ± 5 min), preventing mass cache expiry

Cache structure in Redis:
```json
{
  "data": { "id": 1, "name": "iPhone 15 Pro", ... },
  "logicalExpireTime": 1774858233258
}
```

### 2. MySQL Read-Write Separation

Located in `product-service/src/main/java/com/course/ecommerce/config/`:

- Dual HikariCP data sources: HikariPool-1 (Master:mysql:3306) + HikariPool-2 (Slave:mysql-slave:3306)
- `AbstractRoutingDataSource` + `@ReadOnly` annotation + AOP aspect for automatic routing
- Read operations (e.g., `selectById`) route to slave; writes route to master

### 3. ElasticSearch Product Search

Located in `product-service/src/main/java/com/course/ecommerce/elasticsearch/`:

- `@PostConstruct` full sync from MySQL to ES on service startup (5 products indexed)
- Full-text search via `MatchQuery` on product name field
- REST endpoint: `GET /api/products/search?q=keyword`

### 4. Nginx Load Balancing

Located in `nginx/nginx.conf`:

- Round-robin distribution across two product-service instances
- `X-Instance-Id` response header identifies which backend served the request
- Static files served directly with `1d` cache headers

### 5. Instance Identification

Located in `product-service/src/main/java/com/course/ecommerce/config/InstanceIdInterceptor.java`:

- Each product-service container exposes its identity via `X-Instance-Id` response header
- Configured via `APP_INSTANCE_ID` environment variable in Docker Compose

### 6. Docker Healthchecks

All 10 containers define `healthcheck` in `docker-compose.yml`:
- **nginx**: `curl -f http://localhost/health`
- **Java services**: `wget -qO- http://localhost:{port}/api/{service}/health`
- **mysql**: `mysqladmin ping`
- **redis**: `redis-cli -a redis123 ping`
- **elasticsearch**: `curl -f http://localhost:9200/_cluster/health`

## Project Structure

```
distributed_system_course/
├── common-core/           # Shared: Result, BusinessException, JwtUtil, User entity, DTOs
├── user-service/          # Registration, login, BCrypt password hashing
├── product-service/       # Product API + Redis cache (3-layer protection) + ES search + R/W separation
│   ├── config/
│   │   ├── MyBatisConfig.java            # Dual datasource + routing config
│   │   ├── ReadOnly.java                 # @ReadOnly annotation
│   │   ├── ReadOnlyRoutingAspect.java    # AOP read-write routing
│   │   ├── ElasticsearchConfig.java      # ES client config
│   │   └── InstanceIdInterceptor.java    # X-Instance-Id header
│   ├── elasticsearch/
│   │   ├── ProductDocument.java          # ES document model
│   │   └── ProductSearchService.java     # Sync + search logic
│   ├── service/
│   │   ├── ProductCacheService.java      # Redis cache with penetration/breakdown/avalanche protection
│   │   └── impl/ProductServiceImpl.java  # 3-protection cache logic
│   └── controller/ProductController.java # REST endpoints including /search
├── inventory-service/     # Stock query (minimal)
├── order-service/         # Order CRUD (minimal)
├── sql/init.sql           # Schema + 5 Apple products + test data
├── nginx/
│   ├── nginx.conf         # Upstream + location /api/ + static files
│   └── html/              # index.html, style.css, app.js
├── jmeter/
│   ├── product-detail-test.jmx   # 3-phase: warmup → concurrency → LB check
│   ├── static-file-test.jmx      # index.html, style.css, app.js load test
│   └── results/                  # .jtl output files (git-ignored)
├── docker-compose.yml             # Full stack (all 10 services)
├── docker-compose-step4.yml       # Subset: nginx + product × 2 (for Step 4)
├── pom.xml                        # Parent POM (multi-module aggregator)
├── CLAUDE.md                      # Claude Code guidance
└── docs/                          # Architecture, API, database documentation
```

## API Endpoints

| Method | Path | Service | Description |
|--------|------|---------|-------------|
| GET | `/` | nginx | Homepage (static HTML) |
| GET | `/api/products/{id}` | product-service | Product detail (Redis cached, logical expiry) |
| GET | `/api/products/search?q=` | product-service | Product search (ElasticSearch) |
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

Test data includes 5 Apple products (iPhone 15 Pro, MacBook Pro, AirPods Pro, iPad Air, Apple Watch) and 1 test user.

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

### JMeter connection errors from Docker
Run JMeter on the same Docker network:
```bash
docker run --rm --network distributed_system_course_ecommerce-net \
  -v $(pwd)/jmeter:/jmeter alpine/jmeter:latest \
  -n -t /jmeter/product-detail-test.jmx -l /jmeter/results/product-detail.jtl
```

## Step Completion

All 5 course steps + bonus enhancements are complete:

- **Step 1**: Multi-module Maven skeleton
- **Step 2**: Core service split + minimal interfaces
- **Step 3**: Redis caching on product-service with risk governance
- **Step 4**: Nginx gateway + dual product-service + load balancing
- **Step 5**: Docker Compose orchestration + JMeter load testing
- **Bonus**: Cache triple protection (penetration/breakdown/avalanche) + MySQL read-write separation + ElasticSearch search

See `docs/superpowers/step*-*.md` for detailed progress reports and debugging notes.
