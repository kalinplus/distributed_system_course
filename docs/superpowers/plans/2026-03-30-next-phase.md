# Next Phase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Complete three专项工程: (1) cache penetration/breakdown production code, (2) MySQL read-write separation, (3) ElasticSearch product search.

**Architecture:**
- **Cache**: Logic-expiry pattern — cache stores `{data, logicalExpire}`; on miss, one thread acquires SETNX lock and async refreshes; others return stale data. `setNullValue()` called on DB null.
- **Read-Write Split**: Two MySQL instances (master on 3306, slave on 3307) sharing the same data. Spring `AbstractRoutingDataSource` routes reads → slave, writes → master. For this course, the "master" and "slave" are logically separated via routing; binlog replication is out of scope.
- **Search**: Single-node ElasticSearch 8.x in Docker. Product data synced from MySQL on startup via a `@PostConstruct` init task. Keyword search via `match` query.

**Tech Stack:** Spring Boot 3.2, StringRedisTemplate (SETNX), Spring `AbstractRoutingDataSource`, Elasticsearch Java Client 8.x, JMeter 5.6.3

---

## FILE MAP

```
product-service/src/main/java/com/course/ecommerce/
├── controller/
│   └── ProductController.java       [MODIFY] add /search endpoint
├── service/
│   ├── ProductCacheService.java     [MODIFY] add lock + logic-expiry methods
│   ├── ProductService.java          [MODIFY] add interface method signature
│   ├── ProductServiceImpl.java      [MODIFY] call setNullValue + lock logic
│   └── impl/
│       └── ProductServiceImpl.java  [MODIFY] same as above
├── config/
│   └── DataSourceRouter.java        [CREATE] AbstractRoutingDataSource
│   └── DataSourceNames.java         [CREATE] enum MASTER/SLAVE
├── elasticsearch/
│   ├── ProductDocument.java         [CREATE] @Document model
│   └── ProductSearchService.java    [CREATE] index + search logic
└── mapper/
    └── ProductMapper.java           [MODIFY] add @ReadOnly on read methods

sql/
└── init.sql                         [NO CHANGE]

docker-compose.yml                    [MODIFY] add mysql-slave + elasticsearch services

jmeter/
└── cache-penetration-test.jmx       [CREATE] penetration verification test
```

---

## PART A: Cache Penetration & Breakdown (Production Code)

### Task A1: Upgrade ProductCacheService with lock + logic-expiry

**Files:**
- Modify: `product-service/src/main/java/com/course/ecommerce/service/ProductCacheService.java`

- [ ] **Step 1: Read the existing file**

Read `ProductCacheService.java` in full to confirm current state.

- [ ] **Step 2: Add lock acquisition method**

Add after `setNullValue()`:

```java
private static final String LOCK_KEY_PREFIX = "product:lock:";
private static final long LOCK_EXPIRE_SECONDS = 10;

/**
 * 获取互斥锁（SETNX），用于缓存击穿防护
 * @return true = 获取成功，当前线程负责回源; false = 其他线程已获取锁
 */
public boolean acquireLock(Long productId) {
    String lockKey = LOCK_KEY_PREFIX + productId;
    try {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    } catch (Exception e) {
        return false;
    }
}

/**
 * 释放互斥锁
 */
public void releaseLock(Long productId) {
    String lockKey = LOCK_KEY_PREFIX + productId;
    try {
        redisTemplate.delete(lockKey);
    } catch (Exception e) {
        // ignore
    }
}
```

- [ ] **Step 3: Add logic-expiry cache data class**

Add as a static inner class at the bottom of `ProductCacheService.java`:

```java
/**
 * 逻辑过期缓存对象
 */
public static class CacheData<T> {
    private T data;
    private long logicalExpireTime; // 逻辑过期时间戳（毫秒）

    public CacheData(T data, long logicalExpireTime) {
        this.data = data;
        this.logicalExpireTime = logicalExpireTime;
    }

    public T getData() { return data; }
    public long getLogicalExpireTime() { return logicalExpireTime; }
    public boolean isExpired() { return System.currentTimeMillis() > logicalExpireTime; }
}
```

- [ ] **Step 4: Add logic-expiry getProductWithLogicExpire**

```java
private static final long LOGICAL_EXPIRE_MINUTES = 30;

/**
 * 逻辑过期模式：获取缓存，判断是否过期
 * 返回 Optional 含义：
 *   - empty() = 缓存不存在，需回源
 *   - present + expired = 缓存已过期，需异步刷新
 *   - present + not expired = 缓存有效
 */
public Optional<CacheData<Product>> getProductWithLogicExpire(Long productId) {
    if (productId == null) {
        throw new IllegalArgumentException("productId cannot be null");
    }
    String cacheKey = CACHE_KEY_PREFIX + productId;
    String json = redisTemplate.opsForValue().get(cacheKey);

    if (json == null) {
        return Optional.empty(); // 缓存不存在
    }

    try {
        CacheData<Product> cached = objectMapper.readValue(json,
            objectMapper.getTypeFactory().constructParametricType(CacheData.class, Product.class));
        return Optional.of(cached);
    } catch (Exception e) {
        redisTemplate.delete(cacheKey);
        return Optional.empty();
    }
}
```

- [ ] **Step 5: Add setProductWithLogicExpire**

```java
/**
 * 设置逻辑过期的缓存（雪崩防护：TTL jitter 仍然保留）
 */
public void setProductWithLogicExpire(Product product) {
    String cacheKey = CACHE_KEY_PREFIX + product.getId();
    try {
        long logicalExpire = System.currentTimeMillis() + LOGICAL_EXPIRE_MINUTES * 60 * 1000;
        CacheData<Product> cacheData = new CacheData<>(product, logicalExpire);
        String json = objectMapper.writeValueAsString(cacheData);
        // 物理 TTL = 逻辑过期时间 + 一定余量（如 5 分钟），确保数据不提前被 Redis 回收
        long physicalTtlMinutes = LOGICAL_EXPIRE_MINUTES + 5;
        redisTemplate.opsForValue().set(cacheKey, json, physicalTtlMinutes, TimeUnit.MINUTES);
    } catch (Exception e) {
        // ignore
    }
}
```

---

### Task A2: Update ProductServiceImpl to use all three protections

**Files:**
- Modify: `product-service/src/main/java/com/course/ecommerce/service/impl/ProductServiceImpl.java`

- [ ] **Step 1: Read the existing file**

Read the current `ProductServiceImpl.java` (already done above — 46 lines, cache-aside only).

- [ ] **Step 2: Rewrite getProductById with three protections**

Replace the entire `getProductById` method:

```java
@Override
public Product getProductById(Long id) {
    // ========== 穿透防护第一层：逻辑过期缓存 ==========
    Optional<ProductCacheService.CacheData<Product>> cached =
            productCacheService.getProductWithLogicExpire(id);

    if (cached.isPresent()) {
        ProductCacheService.CacheData<Product> data = cached.get();
        if (!data.isExpired()) {
            // 缓存命中且未过期，直接返回
            logger.info("Cache hit (logical, valid) for product: {}", id);
            return data.getData();
        }
        // 缓存命中但已过期 → 击穿防护
        logger.info("Cache expired (logical) for product: {}", id);
        boolean locked = productCacheService.acquireLock(id);
        if (!locked) {
            // 未拿到锁，说明另一个线程正在回源 → 短暂等待后重新查缓存
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Optional<ProductCacheService.CacheData<Product>> recheck =
                    productCacheService.getProductWithLogicExpire(id);
            if (recheck.isPresent() && !recheck.get().isExpired()) {
                logger.info("Got refreshed data from sibling thread for product: {}", id);
                return recheck.get().getData();
            }
            // 仍然拿不到，返回null让上层处理（不查DB）
            logger.warn("Other thread is rebuilding cache, returning null for: {}", id);
            return null;
        }
        // 拿到锁：异步刷新缓存（不阻塞当前请求）
        final Long productId = id;
        Thread asyncRefresh = new Thread(() -> {
            try {
                logger.info("Async cache refresh started for product: {}", productId);
                Product refreshed = productMapper.selectById(productId);
                if (refreshed != null) {
                    productCacheService.setProductWithLogicExpire(refreshed);
                } else {
                    productCacheService.setNullValue(productId);
                }
                logger.info("Async cache refresh done for product: {}", productId);
            } catch (Exception e) {
                logger.error("Async cache refresh failed for product: {}", productId, e);
            } finally {
                productCacheService.releaseLock(productId);
            }
        });
        asyncRefresh.setName("cache-refresh-" + id);
        asyncRefresh.start();
        // 当前请求返回过期数据（优雅降级）
        logger.info("Returning stale data for product: {}", id);
        return data.getData();
    }

    // ========== 缓存完全不存在：回源查库 ==========
    logger.info("Cache miss for product: {}", id);
    Product product = productMapper.selectById(id);

    if (product == null) {
        // ========== 穿透防护：缓存空值 ==========
        logger.info("Product not found, caching null value for: {}", id);
        productCacheService.setNullValue(id);
        throw new BusinessException(404, "商品不存在");
    }

    // ========== 写入逻辑过期缓存 ==========
    productCacheService.setProductWithLogicExpire(product);
    return product;
}
```

- [ ] **Step 3: Add import**

Ensure these imports are present:

```java
import com.course.ecommerce.service.ProductCacheService.CacheData;
```

---

### Task A3: Add product search to controller

**Files:**
- Modify: `product-service/src/main/java/com/course/ecommerce/controller/ProductController.java`

- [ ] **Step 1: Add search endpoint**

Add field and new endpoint after the existing ones:

```java
@Autowired
private com.course.ecommerce.elasticsearch.ProductSearchService productSearchService;

@GetMapping("/search")
public Result<?> search(@RequestParam(required = false, defaultValue = "") String q) {
    if (q == null || q.trim().isEmpty()) {
        return Result.success(java.util.Collections.emptyList());
    }
    return Result.success(productSearchService.search(q.trim()));
}
```

---

### Task A4: Build product-service with new code and deploy

**Files:**
- Modify: `product-service/src/main/java/...` (all files above)

- [ ] **Step 1: Build using Maven Docker container**

```bash
docker run --rm \
  -v "//f/github/distributed_system_course:/workspace" \
  -w /workspace \
  docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17 \
  mvn package -DskipTests -pl product-service -am
```

Expected: `BUILD SUCCESS`. JAR at `product-service/target/product-service-1.0.0.jar`.

- [ ] **Step 2: Rebuild and restart product containers**

```bash
cd F:/github/distributed_system_course
docker compose up -d --build product-service-1 product-service-2
```

- [ ] **Step 3: Verify penetration protection**

```bash
# 查询不存在商品 → 应命中空值缓存，不查DB
curl http://localhost/api/products/999999
# 期望: HTTP 404，body: {"code":404,"message":"商品不存在"}

# 检查 Redis 中存在空值缓存
docker exec redis redis-cli -a redis123 GET "product:detail:999999"
# 期望: "null"
```

- [ ] **Step 4: Verify breakdown protection (log check)**

```bash
# 手动触发缓存失效（删除key）
docker exec redis redis-cli -a redis123 DEL "product:detail:1"

# 并发请求（5个同时打）
for i in $(seq 1 5); do curl -s http://localhost/api/products/1 & done; wait

# 检查日志：只有1条 "Cache miss" 或 "Async cache refresh started"
docker logs product-service-1 --since 10s 2>&1 | grep -E "Cache miss|Async|locked|refresh"
# 期望: 只有 1 条 "Cache miss"，其他请求不触发 DB 查询
```

---

### Task A5: Create cache penetration JMeter test

**Files:**
- Create: `jmeter/cache-penetration-test.jmx`

- [ ] **Step 1: Create the JMX file**

Create a minimal JMeter test plan that:
- Thread Group: 1 thread, 1 ramp-up, 1 loop
- HTTP Request: `GET http://localhost/api/products/999999`
- Response Assertion: `code` equals `404`
- Listener: write results to `jmeter/results/cache-penetration.jtl`

Use this as the template — copy structure from existing `product-detail-test.jmx` and adapt:

```xml
<!-- See existing jmx structure for reference -->
<!-- Key difference: uses product ID 999999 which does not exist in DB -->
<!-- Assertion: response JSON contains "code":404 -->
```

The JMX can be created by copying `product-detail-test.jmx` and modifying:
- The product ID from `1` to `999999`
- The response assertion from `200` to `404`
- The thread count to `50` for a burst of non-existent IDs

- [ ] **Step 2: Run the test**

```bash
# From Windows host (JMeter can reach localhost)
python3 -c "
import subprocess
r = subprocess.run([
    'curl', '-s', 'http://localhost/api/products/999999'
], capture_output=True, text=True)
print(r.stdout)
"
# Expected: {\"code\":404,\"message\":\"商品不存在\",\"data\":null}
```

Then verify Redis:
```bash
docker exec redis redis-cli -a redis123 GET "product:detail:999999"
# Expected: "null"
```

---

## PART B: MySQL Read-Write Separation

### Task B1: Add mysql-slave to docker-compose.yml

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: Add mysql-slave service definition**

Add after the existing `mysql` service block:

```yaml
  # -----------------------------------------------
  # MySQL Slave (Read Replica)
  # -----------------------------------------------
  mysql-slave:
    image: mysql:8.0
    container_name: mysql-slave
    ports:
      - "3307:3306"
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: ecommerce
    volumes:
      - ./sql/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
      - mysql_slave_data:/var/lib/mysql
    command: >
      --server-id=2
      --read-only=ON
      --log-bin=mysql-bin
      --binlog-format=ROW
      --skip-name-resolve
    networks:
      - ecommerce-net
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 3
```

- [ ] **Step 2: Add volume for mysql-slave**

Add to the `volumes:` section at the bottom of `docker-compose.yml`:

```yaml
mysql_slave_data:
```

- [ ] **Step 3: Stop existing containers and restart with new config**

```bash
cd F:/github/distributed_system_course
docker compose down
docker compose up -d mysql mysql-slave redis
sleep 20 && docker compose ps
# Expect: mysql (healthy), mysql-slave (healthy), redis (healthy)
```

---

### Task B2: Create routing data source config

**Files:**
- Create: `product-service/src/main/java/com/course/ecommerce/config/DataSourceNames.java`
- Create: `product-service/src/main/java/com/course/ecommerce/config/DataSourceRouter.java`
- Create: `product-service/src/main/java/com/course/ecommerce/config/MyBatisConfig.java`

- [ ] **Step 1: Create DataSourceNames enum**

```java
package com.course.ecommerce.config;

/**
 * 数据源名称枚举
 */
public enum DataSourceNames {
    MASTER,
    SLAVE
}
```

- [ ] **Step 2: Create DataSourceRouter**

```java
package com.course.ecommerce.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 动态数据源路由
 * 通过 DataSourceContextHolder 设置的 key 决定使用哪个数据源
 */
public class DataSourceRouter extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.getDataSource();
    }
}
```

- [ ] **Step 3: Create DataSourceContextHolder (thread-local holder)**

```java
package com.course.ecommerce.config;

/**
 * 数据源上下文持有者（ThreadLocal）
 */
public class DataSourceContextHolder {

    private static final ThreadLocal<DataSourceNames> CONTEXT = new ThreadLocal<>();

    public static void setDataSource(DataSourceNames name) {
        CONTEXT.set(name);
    }

    public static DataSourceNames getDataSource() {
        DataSourceNames name = CONTEXT.get();
        return name == null ? DataSourceNames.MASTER : name; // default to master
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
```

- [ ] **Step 4: Create MyBatisConfig with dual data sources**

```java
package com.course.ecommerce.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@MapperScan("com.course.ecommerce.mapper")
public class MyBatisConfig {

    // ---------- Master DataSource ----------
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSource masterDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:mysql://mysql:3306/ecommerce?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
        ds.setUsername("ecommerce");
        ds.setPassword("ecommerce123");
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        return ds;
    }

    // ---------- Slave DataSource ----------
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.slave")
    public DataSource slaveDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:mysql://mysql-slave:3306/ecommerce?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
        ds.setUsername("ecommerce");
        ds.setPassword("ecommerce123");
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        return ds;
    }

    // ---------- Routing DataSource ----------
    @Bean
    @Primary
    public DataSource routingDataSource(
            @Qualifier("masterDataSource") DataSource master,
            @Qualifier("slaveDataSource") DataSource slave) {
        DataSourceRouter router = new DataSourceRouter();
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceNames.MASTER, master);
        targetDataSources.put(DataSourceNames.SLAVE, slave);
        router.setTargetDataSources(targetDataSources);
        router.setDefaultTargetDataSource(master); // default = master
        return router;
    }

    // ---------- SqlSessionFactory ----------
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource routingDataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(routingDataSource);
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath:mapper/*.xml"));
        return factory.getObject();
    }
}
```

---

### Task B3: Add AOP aspect for read/write routing

**Files:**
- Create: `product-service/src/main/java/com/course/ecommerce/config/ReadOnlyRoutingAspect.java`
- Modify: `product-service/src/main/java/com/course/ecommerce/mapper/ProductMapper.java`

- [ ] **Step 1: Create ReadOnly annotation**

```java
package com.course.ecommerce.config;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReadOnly {
}
```

- [ ] **Step 2: Create AOP Aspect**

```java
package com.course.ecommerce.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(0)
public class ReadOnlyRoutingAspect {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyRoutingAspect.class);

    @Around("@annotation(ReadOnly)")
    public Object routeToSlave(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            DataSourceContextHolder.setDataSource(DataSourceNames.SLAVE);
            log.debug("[READ-SLAVE] routing to slave: {}", joinPoint.getSignature().getName());
            return joinPoint.proceed();
        } finally {
            DataSourceContextHolder.clear();
        }
    }
}
```

- [ ] **Step 3: Modify ProductMapper — mark read methods**

Add `@ReadOnly` annotation to all query methods in `ProductMapper.java`:

```java
package com.course.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.ecommerce.config.ReadOnly;
import com.course.ecommerce.entity.Product;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @ReadOnly
    @Select("SELECT * FROM t_product WHERE id = #{id}")
    Product selectById(Long id);
}
```

- [ ] **Step 4: Verify compilation**

```bash
docker run --rm \
  -v "//f/github/distributed_system_course:/workspace" \
  -w /workspace \
  docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17 \
  mvn package -DskipTests -pl product-service -am
```

---

### Task B4: Verify read-write routing with logs

- [ ] **Step 1: Restart all containers**

```bash
cd F:/github/distributed_system_course
docker compose up -d --build
sleep 25 && docker compose ps
# Expect all 9 containers (including mysql-slave + elasticsearch later) healthy
```

- [ ] **Step 2: Check read routing logs**

```bash
curl http://localhost/api/products/1
# Expected in product-service-1 log:
#   [READ-SLAVE] routing to slave: selectById
docker logs product-service-1 --since 10s 2>&1 | grep "READ-SLAVE"
```

- [ ] **Step 3: Verify slave is read-only**

```bash
docker exec mysql-slave mysql -uroot -proot123 -e "SHOW VARIABLES LIKE 'read_only';"
# Expected: read_only = ON
```

---

## PART C: ElasticSearch Product Search

### Task C1: Add Elasticsearch to docker-compose.yml

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: Add elasticsearch service**

Add before the `networks:` section at the bottom:

```yaml
  # -----------------------------------------------
  # ElasticSearch
  # -----------------------------------------------
  elasticsearch:
    image: elasticsearch:8.11.0
    container_name: elasticsearch
    environment:
      - discovery.type=single-node
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
      - xpack.security.enabled=false
      - xpack.security.enrollment.enabled=false
    ports:
      - "9200:9200"
    volumes:
      - es_data:/usr/share/elasticsearch/data
    networks:
      - ecommerce-net
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:9200/_cluster/health || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 5

volumes:
  es_data:
```

---

### Task C2: Add ES dependencies to product-service pom.xml

**Files:**
- Modify: `product-service/pom.xml`

- [ ] **Step 1: Add Elasticsearch client dependency**

Add after the existing `spring-boot-starter-data-redis` dependency:

```xml
<!-- Elasticsearch -->
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>8.11.0</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

---

### Task C3: Create ES document and search service

**Files:**
- Create: `product-service/src/main/java/com/course/ecommerce/elasticsearch/ProductDocument.java`
- Create: `product-service/src/main/java/com/course/ecommerce/elasticsearch/ProductSearchService.java`

- [ ] **Step 1: Create ProductDocument**

```java
package com.course.ecommerce.elasticsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * ES 文档模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDocument {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String category;
    private String imageUrl;
    private Integer status;
}
```

- [ ] **Step 2: Create ProductSearchService**

```java
package com.course.ecommerce.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson_data.JacksonDataMapper;
import com.course.ecommerce.entity.Product;
import com.course.ecommerce.mapper.ProductMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);
    private static final String INDEX_NAME = "products";

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private ProductMapper productMapper;

    /**
     * 启动时全量同步 MySQL → ES
     */
    @PostConstruct
    public void syncAllProducts() {
        try {
            List<Product> products = productMapper.selectList(null);
            log.info("Syncing {} products to ElasticSearch", products.size());
            for (Product p : products) {
                indexProduct(p);
            }
            log.info("Product sync to ElasticSearch complete");
        } catch (Exception e) {
            log.warn("Failed to sync products to ES on startup: {}", e.getMessage());
        }
    }

    /**
     * 索引单个商品
     */
    public void indexProduct(Product product) {
        try {
            ProductDocument doc = toDocument(product);
            esClient.index(IndexRequest.of(i -> i
                    .index(INDEX_NAME)
                    .id(String.valueOf(product.getId()))
                    .document(doc)
            ));
        } catch (IOException e) {
            log.error("Failed to index product {}: {}", product.getId(), e.getMessage());
        }
    }

    /**
     * 关键词全文搜索
     */
    public List<ProductDocument> search(String keyword) {
        try {
            Query matchQuery = MatchQuery.of(m -> m
                    .field("name")
                    .field("description")
                    .query(keyword)
            )._toQuery();

            SearchResponse<ProductDocument> response = esClient.search(
                    SearchRequest.of(s -> s
                            .index(INDEX_NAME)
                            .query(matchQuery)
                            .size(20)
                    ),
                    ProductDocument.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Search failed for keyword '{}': {}", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }

    private ProductDocument toDocument(Product p) {
        return new ProductDocument(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getStock(),
                p.getCategory(),
                p.getImageUrl(),
                p.getStatus()
        );
    }
}
```

---

### Task C4: Configure ES client beans

**Files:**
- Create: `product-service/src/main/java/com/course/ecommerce/config/ElasticsearchConfig.java`
- Modify: `product-service/src/main/resources/application-prod.yml`

- [ ] **Step 1: Create ElasticsearchConfig**

```java
package com.course.ecommerce.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.host:localhost}")
    private String host;

    @Value("${spring.elasticsearch.port:9200}")
    private int port;

    @Bean
    public RestClient restClient() {
        return RestClient.builder(new HttpHost(host, port, "http")).build();
    }

    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new JacksonJsonpMapper(mapper);
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(
            RestClient restClient,
            ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
```

- [ ] **Step 2: Add ES config to application-prod.yml**

Add at the bottom of `product-service/src/main/resources/application-prod.yml`:

```yaml
spring:
  elasticsearch:
    host: elasticsearch
    port: 9200
```

---

### Task C5: Build and verify search endpoint

- [ ] **Step 1: Build**

```bash
docker run --rm \
  -v "//f/github/distributed_system_course:/workspace" \
  -w /workspace \
  docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17 \
  mvn package -DskipTests -pl product-service -am
```

- [ ] **Step 2: Deploy all containers**

```bash
cd F:/github/distributed_system_course
docker compose down
docker compose up -d --build
sleep 30 && docker compose ps
# Expect elasticsearch healthy
```

- [ ] **Step 3: Verify ES is up**

```bash
curl http://localhost:9200
# Expected: JSON with cluster_name, version.number
```

- [ ] **Step 4: Verify search endpoint**

```bash
curl "http://localhost/api/products/search?q=iPhone"
# Expected: JSON array with iPhone 15 Pro product

curl "http://localhost/api/products/search?q=MacBook"
# Expected: JSON array with MacBook Pro

curl "http://localhost/api/products/search?q=不存在"
# Expected: empty array []
```

---

## PART D: Final Integration Verification

### Task D1: Full smoke test

- [ ] **Step 1: Penetration test**

```bash
curl -s http://localhost/api/products/999999
docker exec redis redis-cli -a redis123 GET "product:detail:999999"
```

- [ ] **Step 2: Breakdown test**

```bash
docker exec redis redis-cli -a redis123 DEL "product:detail:1"
python3 -c "
import subprocess, concurrent.futures
def call(_):
    r = subprocess.run(['curl','-s','http://localhost/api/products/1'], capture_output=True, text=True)
    return r.stdout
with concurrent.futures.ThreadPoolExecutor(max_workers=10) as ex:
    results = list(ex.map(call, range(10)))
print('All returned 200:', all('iPhone' in r for r in results))
"
```

- [ ] **Step 3: Read-write split**

```bash
curl -s http://localhost/api/products/1 | grep -q "iPhone" && echo "READ OK"
docker logs product-service-1 --since 5s 2>&1 | grep "READ-SLAVE"
# Expected: [READ-SLAVE] routing to slave
```

- [ ] **Step 4: Search**

```bash
curl -s "http://localhost/api/products/search?q=iPhone" | grep -q "iPhone" && echo "SEARCH OK"
```

---

## RISK DECISIONS LOG

| Decision | Choice | Reason |
|---|---|---|
| Cache breakdown pattern | Logic expiry + async refresh | Non-blocking, better UX |
| Read-write split scope | RoutingDataSource only (no binlog) | Course goal is "demonstrate routing" |
| ES data sync | Full sync on @PostConstruct | Simplest, acceptable for course scope |
| ES index strategy | Single index, no custom analyzer | "ik 分词可选，了解即可" — default standard analyzer suffices |

---

## SELF-REVIEW CHECKLIST

- [ ] Spec requires `setNullValue()` called in production → Task A2 Step 2 does this
- [ ] Spec requires `SETNX` mutex for breakdown → Task A1 Step 2 adds it
- [ ] Spec requires `jmeter/cache-penetration-test.jmx` → Task A5 creates it
- [ ] Spec requires MySQL master + slave in docker-compose → Task B1 adds both
- [ ] Spec requires read→slave, write→master in code → Tasks B2–B3 implement this
- [ ] Spec requires ES product search → Tasks C1–C5 implement full flow
- [ ] No placeholder "TBD" anywhere in plan steps
- [ ] All method names consistent: `getProductWithLogicExpire`, `setProductWithLogicExpire`, `acquireLock`, `releaseLock`, `setNullValue`
- [ ] Build uses Maven Docker container throughout (no local Maven dependency)
