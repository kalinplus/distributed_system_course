package com.course.ecommerce.service.impl;

import com.course.ecommerce.common.exception.BusinessException;
import com.course.ecommerce.entity.Product;
import com.course.ecommerce.mapper.ProductMapper;
import com.course.ecommerce.service.ProductCacheService;
import com.course.ecommerce.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductServiceImpl.class);

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductCacheService productCacheService;

    @Override
    public Product getProductById(Long id) {
        // 1. 先查缓存
        Optional<Product> cachedProduct = productCacheService.getProduct(id);
        if (cachedProduct.isPresent()) {
            logger.info("Cache hit for product: {}", id);
            return cachedProduct.get();
        }

        // 2. 缓存未命中，查数据库
        logger.info("Cache miss for product: {}", id);
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }

        // 3. 写入缓存
        productCacheService.setProduct(product);

        return product;
    }
}
