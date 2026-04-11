package com.course.ecommerce.controller;

import com.course.ecommerce.common.Result;
import com.course.ecommerce.service.SeckillStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀库存接口
 */
@RestController
@RequestMapping("/api/inventory/seckill")
public class SeckillInventoryController {

    @Autowired
    private SeckillStockService seckillStockService;

    /**
     * 预热库存
     * POST /api/inventory/seckill/warmup/{productId}/{stock}
     */
    @PostMapping("/warmup/{productId}/{stock}")
    public Result<?> warmupStock(
            @PathVariable Long productId,
            @PathVariable Long stock,
            @RequestParam(required = false) Integer expireSeconds) {
        seckillStockService.warmupStock(productId, stock, expireSeconds);
        return Result.success("库存预热成功");
    }

    /**
     * 扣减库存
     * POST /api/inventory/seckill/deduct/{userId}/{productId}/{activityId}/{quantity}
     */
    @PostMapping("/deduct/{userId}/{productId}/{activityId}/{quantity}")
    public Result<?> deductStock(
            @PathVariable Long userId,
            @PathVariable Long productId,
            @PathVariable Long activityId,
            @PathVariable Integer quantity) {
        Long result = seckillStockService.deductStock(userId, productId, activityId, quantity);

        if (result >= 0) {
            return Result.success(result); // 扣减成功，返回剩余库存
        } else if (result == -1) {
            return Result.fail(400, "库存不足");
        } else if (result == -2) {
            return Result.fail(400, "用户已参与");
        } else if (result == -3) {
            return Result.fail(400, "库存未初始化");
        } else {
            return Result.fail(500, "扣减失败");
        }
    }

    /**
     * 查询库存
     * GET /api/inventory/seckill/{productId}
     */
    @GetMapping("/{productId}")
    public Result<?> getStock(@PathVariable Long productId) {
        Long stock = seckillStockService.getStock(productId);
        if (stock == null) {
            return Result.fail(404, "库存未初始化");
        }
        return Result.success(stock);
    }

    /**
     * 回滚库存（用于测试或管理）
     * POST /api/inventory/seckill/rollback/{productId}
     */
    @PostMapping("/rollback/{productId}")
    public Result<?> rollbackStock(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {
        seckillStockService.rollbackStock(productId, quantity);
        return Result.success("库存回滚成功");
    }

    /**
     * 清除用户去重标记（用于测试）
     * POST /api/inventory/seckill/clear/{userId}/{productId}/{activityId}
     */
    @PostMapping("/clear/{userId}/{productId}/{activityId}")
    public Result<?> clearDedupKey(
            @PathVariable Long userId,
            @PathVariable Long productId,
            @PathVariable Long activityId) {
        seckillStockService.clearDedupKey(userId, productId, activityId);
        return Result.success("去重标记清除成功");
    }
}
