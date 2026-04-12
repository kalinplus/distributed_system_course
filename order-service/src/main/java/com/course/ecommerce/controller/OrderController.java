package com.course.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.ecommerce.common.Result;
import com.course.ecommerce.entity.Order;
import com.course.ecommerce.mapper.OrderMapper;
import com.course.ecommerce.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口
 * GET /api/orders/{id}  订单查询(通过ID)
 * GET /api/orders/order/{orderNo}  订单查询(通过订单号)
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderMapper orderMapper;

    @GetMapping("/{id}")
    public Result<?> getOrder(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);
        return Result.success(order);
    }

    @GetMapping("/order/{orderNo}")
    public Result<?> getOrderByOrderNo(@PathVariable String orderNo) {
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getOrderNo, orderNo);
        Order order = orderMapper.selectOne(queryWrapper);
        if (order == null) {
            return Result.fail(404, "订单不存在");
        }
        return Result.success(order);
    }

    @GetMapping("/health")
    public Result<?> health() {
        return Result.success("order-service is running");
    }
}
