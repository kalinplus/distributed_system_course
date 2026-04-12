package com.course.ecommerce.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付回调请求DTO
 */
@Data
public class PaymentCallbackRequest {
    private String orderNo;
    private String paymentNo;
    private String status;  // SUCCESS or FAILED
    private BigDecimal amount;
    private Long payTime;
}
