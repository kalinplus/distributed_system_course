package com.course.ecommerce.service;

import com.course.ecommerce.dto.PaymentCallbackRequest;

/**
 * 支付服务接口
 */
public interface PaymentService {

    /**
     * 处理支付回调
     *
     * @param request 支付回调请求
     */
    void handlePaymentCallback(PaymentCallbackRequest request);
}
