package com.course.ecommerce.controller;

import com.course.ecommerce.common.Result;
import com.course.ecommerce.entity.Order;
import com.course.ecommerce.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口
 * GET /api/orders/{id}  订单查询
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/{id}")
    public Result<?> getOrder(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);
        return Result.success(order);
    }

    @GetMapping("/health")
    public Result<?> health() {
        return Result.success("order-service is running");
    }
}
