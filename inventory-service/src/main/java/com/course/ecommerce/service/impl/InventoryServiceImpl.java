package com.course.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.course.ecommerce.common.exception.BusinessException;
import com.course.ecommerce.entity.Inventory;
import com.course.ecommerce.mapper.InventoryMapper;
import com.course.ecommerce.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventoryServiceImpl implements InventoryService {

    @Autowired
    private InventoryMapper inventoryMapper;

    @Override
    public Inventory getInventoryByProductId(Long productId) {
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        wrapper.eq("product_id", productId);
        Inventory inventory = inventoryMapper.selectOne(wrapper);
        if (inventory == null) {
            throw new BusinessException(404, "库存信息不存在");
        }
        return inventory;
    }
}
