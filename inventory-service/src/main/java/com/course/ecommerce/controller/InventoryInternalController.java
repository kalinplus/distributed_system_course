package com.course.ecommerce.controller;

import com.course.ecommerce.service.SeckillStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存内部接口控制器
 * 用于服务间调用，不对外暴露
 */
@Slf4j
@RestController
@RequestMapping("/api/inventory/internal")
@RequiredArgsConstructor
public class InventoryInternalController {

    private final SeckillStockService seckillStockService;

    /**
     * 回滚库存（内部接口）
     * POST /api/inventory/internal/rollback/{productId}/{quantity}
     *
     * @param productId 商品ID
     * @param quantity  回滚数量
     * @return "OK" 表示成功
     */
    @PostMapping("/rollback/{productId}/{quantity}")
    public String rollbackStock(
            @PathVariable Long productId,
            @PathVariable Integer quantity) {
        log.info("Internal rollback stock request, productId: {}, quantity: {}", productId, quantity);

        seckillStockService.rollbackStock(productId, quantity);

        log.info("Internal rollback stock successful, productId: {}, quantity: {}", productId, quantity);
        return "OK";
    }
}
