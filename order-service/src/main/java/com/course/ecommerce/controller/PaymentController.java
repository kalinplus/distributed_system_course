package com.course.ecommerce.controller;

import com.course.ecommerce.common.Result;
import com.course.ecommerce.dto.PaymentCallbackRequest;
import com.course.ecommerce.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付回调接口
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 支付回调接口
     * POST /api/orders/payment/callback
     *
     * @param request 支付回调请求
     * @return 处理结果
     */
    @PostMapping("/payment/callback")
    public Result<?> paymentCallback(@RequestBody PaymentCallbackRequest request) {
        log.info("Received payment callback, orderNo: {}, paymentNo: {}, status: {}",
                request.getOrderNo(), request.getPaymentNo(), request.getStatus());

        paymentService.handlePaymentCallback(request);

        return Result.success("支付回调处理成功");
    }
}
