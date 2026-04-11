package com.course.ecommerce.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 秒杀订单请求DTO
 */
@Data
public class SeckillOrderRequest {

    /** 商品ID */
    private Long productId;

    /** 秒杀活动ID */
    private Long seckillActivityId;

    /** 购买数量 */
    private Integer quantity;

    /** 订单总金额 */
    private BigDecimal totalAmount;
}
