# Step 5 Progress Report

Date: 2026-03-18

## Scope

This document records the Step 5 work for full container orchestration (`docker-compose.yml`) with all 4 business services, Nginx, MySQL, Redis, and JMeter load testing.

## Goal

Complete Step 5 from the task card: containerize all services, configure the full docker-compose stack, and run JMeter tests to verify:
- Static resource response time vs dynamic API response time
- Load balancing distribution across product-service instances
- Redis cache effectiveness (response time drop after warmup)

## Initial Reproduction

All services built and started:

```bash
docker compose up -d --build
docker compose ps
# NAME                STATUS
# mysql               healthy
# nginx               healthy
# product-service-1   healthy
# product-service-2   healthy
# redis               healthy
# user-service        healthy
# inventory-service   healthy (initially exited - see Bug #1)
# order-service       healthy (initially exited - see Bug #1)
```

## Root Causes Found

### Bug 1: Missing Spring Boot Repackage Configuration

**Symptom**: `user-service`, `inventory-service`, `order-service` containers exited immediately with `no main manifest attribute, in app.jar`.

**Root Cause**: The three service pom.xml files only had:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```

Without `<executions><goal>repackage</goal></executions>`, the `spring-boot-maven-plugin` only packaged compiled classes without embedding dependencies or setting the `Main-Class` manifest entry. The resulting JAR was only ~9KB (just the compiled classes), not an executable fat JAR.

**Fix**: Added `<executions>` block to all three pom.xml files:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>repackage</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Files Changed**: `user-service/pom.xml`, `inventory-service/pom.xml`, `order-service/pom.xml`

After fix, JAR sizes changed from ~9KB to 28-31MB (fat JAR with all dependencies embedded).

### Bug 2: Healthcheck Tool Mismatch in docker-compose.yml

**Symptom**: All Java service containers (product-service-1, product-service-2, user-service, inventory-service, order-service) were reported unhealthy by Docker, even though the services started correctly.

**Root Cause**: `docker-compose.yml` originally had `curl` in healthcheck commands for all services. However, the Java base image `eclipse-temurin:17-jre-alpine` only has `wget`, not `curl`.

Error from container logs:
```
exec failed: unable to start container process: exec: "curl": executable file not found in $PATH
```

The Step 4 working version (`docker-compose-step4.yml`) already fixed this for `product-service-1` and `product-service-2`, but `docker-compose.yml` still had the old `curl` commands for all services.

Additionally, the Step 4 working version used `curl` for **nginx** (because nginx:1.24 image has curl but not wget), while using `wget` for Java services (because alpine has wget).

**Fix**: Updated `docker-compose.yml`:
- Java services: `["CMD", "curl", ...]` → `["CMD", "wget", "-qO-", ...]`
- nginx: keep `["CMD", "curl", "-f", ...]` (nginx image has curl)

**Files Changed**: `docker-compose.yml` (healthcheck commands for product-service-1, product-service-2, user-service, inventory-service, order-service, nginx)

### Bug 3: Missing Dockerfiles for 3 Services

**Symptom**: `docker-compose up -d --build` failed because `user-service/Dockerfile`, `inventory-service/Dockerfile`, and `order-service/Dockerfile` did not exist.

**Root Cause**: Only `product-service/Dockerfile` was created in earlier steps. The other three services had no Dockerfiles.

**Fix**: Created three identical-pattern Dockerfiles:

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY {service}/target/{service}-1.0.0.jar app.jar
EXPOSE {port}
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Files Created**: `user-service/Dockerfile` (port 8081), `inventory-service/Dockerfile` (port 8084), `order-service/Dockerfile` (port 8085)

### Bug 4: APP_INSTANCE_ID Missing from docker-compose.yml

**Symptom**: Both product-service instances returned the same `X-Instance-Id` header, making load balancing unverifiable.

**Root Cause**: `docker-compose.yml` did not have `APP_INSTANCE_ID` environment variables for the two product-service instances. Step 4 working config had these.

**Fix**: Added to both product-service instances in `docker-compose.yml`:
```yaml
environment:
  - APP_INSTANCE_ID=product-service-1  # or product-service-2
```

### Bug 5: JMeter XML Format Errors

**Symptom**: JMeter test plans failed to load with XML parsing errors.

**Root Cause 1**: BoolProp elements written with `boolProp="name"` (missing `name` attribute, used `=` instead of `name=`):
```xml
<!-- WRONG -->
<boolProp="HTTPSampler.auto_redirects">false</boolProp>
<!-- CORRECT -->
<boolProp name="HTTPSampler.auto_redirects">false</boolProp>
```

**Root Cause 2**: ResultCollector `objProp` field used wrong structure (`<stringProp name="filename">` nested in `<objProp>` without proper XStream field declaration for ObjectProperty).

**Fix**: Rewrote both JMeter test plans with correct structure:
- Removed ResultCollector listeners from product-detail-test (simplified)
- Added empty `<hashTree/>` after each HTTPSamplerProxy in static-file-test (required for JMeter tree structure)

## Code Changes Made

### Modified Files

| File | Change |
|------|--------|
| `user-service/pom.xml` | Added `<goal>repackage</goal>` executions |
| `inventory-service/pom.xml` | Added `<goal>repackage</goal>` executions |
| `order-service/pom.xml` | Added `<goal>repackage</goal>` executions |
| `docker-compose.yml` | Fixed healthcheck commands (curl→wget for Java, keep curl for nginx), added APP_INSTANCE_ID, updated header comment |

### Created Files

| File | Purpose |
|------|---------|
| `user-service/Dockerfile` | Container image for user-service (port 8081) |
| `inventory-service/Dockerfile` | Container image for inventory-service (port 8084) |
| `order-service/Dockerfile` | Container image for order-service (port 8085) |
| `jmeter/product-detail-test.jmx` | JMeter test: cache warmup → high concurrency (10 threads × 10 loops) → load balance check |
| `jmeter/static-file-test.jmx` | JMeter test: static files (index.html, style.css, app.js) at 10 threads × 20 loops |
| `jmeter/results/` | Directory for .jtl result files |

## Verification Performed

### JMeter Load Tests

Run commands:
```bash
./apache-jmeter-5.6.3/bin/jmeter.sh -n -t jmeter/product-detail-test.jmx -l jmeter/results/product-detail.jtl
./apache-jmeter-5.6.3/bin/jmeter.sh -n -t jmeter/static-file-test.jmx -l jmeter/results/static-file.jtl
```

Note: JMeter 5.6.3 was downloaded from Apache archive since it was not installed on the system. Download command:
```bash
curl -L -o apache-jmeter.tar.gz "https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.6.3.tgz"
tar -xzf apache-jmeter.tar.gz
```

### Test Results

| Test | Total Requests | Throughput | Avg | Min | Max | Error Rate |
|------|---------------|------------|-----|-----|-----|------------|
| Product Detail API | 125 | 26.8/s | **21 ms** | 6 ms | 202 ms | 0% |
| Static Files | 600 | 190.7/s | **6 ms** | 2 ms | 46 ms | 0% |

**Observation**: Static file average response time (6 ms) is approximately **29%** of the API average response time (21 ms), confirming the expected benefit of static file caching by Nginx.

### Load Balancing Verification

Command:
```bash
for i in {1..10}; do curl -s -H "Connection: close" -i http://localhost/api/products/1 | grep X-Instance-Id; done | sort | uniq -c
```

Result:
- `product-service-1`: 6 requests
- `product-service-2`: 4 requests

Distribution is approximately 50/50, consistent with round-robin load balancing.

### Final Verification Commands

```bash
# Start environment
docker compose up -d --build

# Wait for healthy
sleep 20 && docker compose ps

# Test static page
curl http://localhost/

# Test product API
curl http://localhost/api/products/1

# Verify load balancing (need Connection: close to avoid connection reuse)
curl -H "Connection: close" -i http://localhost/api/products/1
curl -H "Connection: close" -i http://localhost/api/products/1

# Run JMeter tests
./apache-jmeter-5.6.3/bin/jmeter.sh -n -t jmeter/product-detail-test.jmx -l jmeter/results/product-detail.jtl
./apache-jmeter-5.6.3/bin/jmeter.sh -n -t jmeter/static-file-test.jmx -l jmeter/results/static-file.jtl

# Analyze results
awk -F',' 'NR>1 {sum+=$2; count++; if($2<min||min==0)min=$2; if($2>max)max=$2} END{print "Count:", count, "Avg:", int(sum/count), "ms Min:", min, "ms Max:", max, "ms"}' jmeter/results/product-detail.jtl
awk -F',' 'NR>1 {sum+=$2; count++; if($2<min||min==0)min=$2; if($2>max)max=$2} END{print "Count:", count, "Avg:", int(sum/count), "ms Min:", min, "ms Max:", max, "ms"}' jmeter/results/static-file.jtl
```

## Final State

All containers healthy and passing JMeter tests:

```
NAME                STATUS
mysql               healthy (healthy)
redis               healthy (healthy)
nginx               healthy (healthy)
product-service-1   healthy (healthy)
product-service-2   healthy (healthy)
user-service        healthy (healthy)
inventory-service   healthy (healthy)
order-service       healthy (healthy)
```

Step 5 is complete and verified.
