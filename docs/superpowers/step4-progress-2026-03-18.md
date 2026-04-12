# Step 4 Progress Report

Date: 2026-03-18

## Scope

This document records the Step 4 verification and debugging work for the Nginx gateway, dual `product-service` instances, MySQL, and Redis stack defined in `docker-compose-step4.yml`.

## Initial Reproduction

The requested Step 4 flow was reproduced with Docker Compose:

```bash
docker compose -f docker-compose-step4.yml up -d --build
curl http://localhost/
curl http://localhost/api/products/1
```

Observed behavior during debugging:

- The homepage probe returned HTTP `200`
- The product API initially returned an application error payload instead of product data
- Product-service logs showed MySQL authentication failures for `ecommerce`

## Root Causes Found

### 1. Stale MySQL volume state

The live MySQL container did not contain the `ecommerce` user even though `sql/init.sql` already defined it. The actual problem was stale Docker volume state: the current `init.sql` was not being re-applied.

Evidence:

- Product-service logs showed `Access denied for user 'ecommerce'@'...'`
- Inspecting the MySQL users inside the container showed no `ecommerce` account before cleanup

Resolution:

```bash
docker compose -f docker-compose-step4.yml down -v
docker compose -f docker-compose-step4.yml up -d --build
```

After recreating the volumes, MySQL initialized from `sql/init.sql` and the `ecommerce` user plus grants were present.

### 2. Product-service healthcheck was using the wrong tool

The product-service containers were running, but Docker marked them unhealthy because the healthcheck used `curl` and the cached `eclipse-temurin:17-jre-alpine` image already had `wget` but not `curl`.

Evidence:

- Docker health log showed `exec: "curl": executable file not found in $PATH`
- Running a one-off container from the same base image showed `/usr/bin/wget` was available

Resolution:

- Updated `docker-compose-step4.yml` product-service healthchecks to use:

```yaml
["CMD", "wget", "-qO-", "http://localhost:8082/api/products/health"]
```

### 3. Instance header was not unique per container

Both product-service instances were exposing the same header value because the old logic derived identity from `server.port`, and both containers run internally on port `8082`.

Resolution:

- Added explicit `APP_INSTANCE_ID` values in Compose:
  - `product-service-1`
  - `product-service-2`
- Updated the interceptor to use `app.instance-id` when present and fall back to `product-service:<server.port>`

### 4. Load-balancing verification needed connection-close requests

Simple repeated requests could appear to hit a single backend because client connection reuse can hide upstream alternation.

Reliable verification command:

```bash
curl -H "Connection: close" -i http://localhost/api/products/1
```

## Code Changes Made

### `product-service/src/main/java/com/course/ecommerce/config/InstanceIdInterceptor.java`

- Added `@Value("${app.instance-id:}")`
- Resolved `X-Instance-Id` from `app.instance-id` when configured
- Kept fallback behavior based on `server.port`

### `product-service/src/test/java/com/course/ecommerce/config/InstanceIdInterceptorTest.java`

- Added a focused unit test proving the interceptor uses the explicit instance ID when configured

### `docker-compose-step4.yml`

- Added:
  - `APP_INSTANCE_ID=product-service-1`
  - `APP_INSTANCE_ID=product-service-2`
- Changed product-service healthchecks from `curl` to `wget`

### `product-service/Dockerfile`

- Kept the image network-free during build
- Continued using the repackaged application jar directly

## Verification Performed

### Unit and module tests

Command:

```bash
mvn -pl product-service test
```

Result:

- 16 tests run
- 0 failures
- 0 errors

### Fresh runtime verification

Commands used:

```bash
docker compose -f docker-compose-step4.yml down -v
docker compose -f docker-compose-step4.yml up -d --build
docker compose -f docker-compose-step4.yml ps
curl http://localhost/
curl http://localhost/api/products/1
curl -H "Connection: close" -i http://localhost/api/products/1
curl -H "Connection: close" -i http://localhost/api/products/1
```

Results:

- `mysql` healthy
- `redis` healthy
- `product-service-1` healthy
- `product-service-2` healthy
- `nginx` healthy
- Homepage returned HTTP `200`
- Product API returned `{"code":200,...}` with product data
- Two consecutive connection-close requests returned:
  - `X-Instance-Id: product-service-1`
  - `X-Instance-Id: product-service-2`

## Final State

Step 4 is working in the current workspace:

- The stack builds and starts successfully
- The product API reads from the initialized MySQL database
- The product-service containers report healthy status
- The Nginx gateway returns product data correctly
- Load balancing is verifiable through distinct `X-Instance-Id` headers
