package com.course.ecommerce.service.impl;

import com.course.ecommerce.common.exception.BusinessException;
import com.course.ecommerce.entity.Product;
import com.course.ecommerce.mapper.ProductMapper;
import com.course.ecommerce.service.ProductCacheService;
import com.course.ecommerce.service.ProductCacheService.CacheData;
import com.course.ecommerce.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductServiceImpl.class);

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductCacheService productCacheService;

    @Override
    public Product getProductById(Long id) {
        // ========== 穿透防护第一层：逻辑过期缓存 ==========
        var cached = productCacheService.getProductWithLogicExpire(id);

        if (cached.isPresent()) {
            CacheData<Product> data = cached.get();
            // 空值缓存命中（穿透防护）：data 为 null 说明商品不存在
            if (data.getData() == null) {
                logger.info("Null cache hit (penetration protection) for product: {}", id);
                throw new BusinessException(404, "商品不存在");
            }
            if (!data.isExpired()) {
                // 缓存命中且未过期，直接返回
                logger.info("Cache hit (logical, valid) for product: {}", id);
                return data.getData();
            }
            // 缓存命中但已过期 → 击穿防护
            logger.info("Cache expired (logical) for product: {}", id);
            boolean locked = productCacheService.acquireLock(id);
            if (!locked) {
                // 未拿到锁：另一个线程正在回源，短暂等待后重查缓存
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                var recheck = productCacheService.getProductWithLogicExpire(id);
                if (recheck.isPresent() && !recheck.get().isExpired()) {
                    logger.info("Got refreshed data from sibling thread for product: {}", id);
                    return recheck.get().getData();
                }
                // 仍拿不到，返回null（不查DB）
                logger.warn("Other thread is rebuilding cache, returning null for: {}", id);
                return null;
            }
            // 拿到锁：异步刷新缓存（不阻塞当前请求）
            Long productId = id;
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
}
