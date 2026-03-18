# Step 4 Runtime Fixes Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Step 4 verification reliable by giving each product-service container a distinct instance ID and by fixing the product-service container healthcheck path.

**Architecture:** Keep the runtime shape unchanged. Use one explicit environment variable for instance identity, keep the existing interceptor/header flow, and make the container image satisfy the existing healthcheck command instead of redesigning the healthcheck contract.

**Tech Stack:** Spring Boot 3, JUnit 5, Mockito, Docker Compose, Alpine-based Temurin JRE image

---

### Task 1: Instance Identity Behavior

**Files:**
- Create: `product-service/src/test/java/com/course/ecommerce/config/InstanceIdInterceptorTest.java`
- Modify: `product-service/src/main/java/com/course/ecommerce/config/InstanceIdInterceptor.java`
- Modify: `docker-compose-step4.yml`

- [ ] **Step 1: Write the failing test**

Write a unit test proving the interceptor prefers an explicit instance ID over the shared container port.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl product-service -Dtest=InstanceIdInterceptorTest test`
Expected: FAIL because the interceptor only uses `server.port`

- [ ] **Step 3: Write minimal implementation**

Add a configurable instance ID property to the interceptor and wire distinct values into the two Step 4 product-service containers.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl product-service -Dtest=InstanceIdInterceptorTest test`
Expected: PASS

### Task 2: Product-Service Healthcheck Runtime

**Files:**
- Modify: `product-service/Dockerfile`

- [ ] **Step 1: Update the runtime image**

Install `curl` in the product-service runtime image so the existing Compose healthcheck command can execute inside the container.

- [ ] **Step 2: Rebuild and verify container health**

Run: `docker compose -f docker-compose-step4.yml up -d --build`
Expected: product-service containers become healthy instead of failing with `curl: executable file not found`

### Task 3: End-to-End Step 4 Verification

**Files:**
- Modify: `docs/superpowers/step4-debug.md`

- [ ] **Step 1: Re-run the documented verification flow**

Run:
- `docker compose -f docker-compose-step4.yml down -v`
- `docker compose -f docker-compose-step4.yml up -d --build`
- `curl http://localhost/`
- `curl http://localhost/api/products/1`

Expected: homepage responds, product API returns product data, and repeated requests expose different `X-Instance-Id` values.

- [ ] **Step 2: Update the debug doc with the actual root causes**

Document that stale MySQL volumes can preserve pre-fix state and that product-service healthcheck failures can come from missing `curl` in the image.
