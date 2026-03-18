package com.course.ecommerce.service;

import com.course.ecommerce.entity.Inventory;

/**
 * 库存服务接口
 */
public interface InventoryService {

    /**
     * 根据商品 ID 查询库存
     * @param productId 商品 ID
     * @return 库存信息
     */
    Inventory getInventoryByProductId(Long productId);
}
