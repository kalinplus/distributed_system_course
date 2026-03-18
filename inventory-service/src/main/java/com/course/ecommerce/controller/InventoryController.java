package com.course.ecommerce.controller;

import com.course.ecommerce.common.Result;
import com.course.ecommerce.entity.Inventory;
import com.course.ecommerce.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存接口
 * GET /api/inventory/{productId}  库存查询
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @GetMapping("/{productId}")
    public Result<?> getInventory(@PathVariable Long productId) {
        Inventory inventory = inventoryService.getInventoryByProductId(productId);
        return Result.success(inventory);
    }

    @GetMapping("/health")
    public Result<?> health() {
        return Result.success("inventory-service is running");
    }
}
