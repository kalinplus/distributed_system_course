package com.course.ecommerce.service;

import com.course.ecommerce.entity.Product;

/**
 * 商品服务接口
 */
public interface ProductService {

    /**
     * 根据 ID 查询商品详情
     * @param id 商品 ID
     * @return 商品信息
     */
    Product getProductById(Long id);
}
