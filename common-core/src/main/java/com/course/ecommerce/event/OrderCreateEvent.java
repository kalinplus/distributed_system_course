package com.course.ecommerce.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单创建事件DTO
 * 用于Kafka消息传递
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 幂等性Key */
    private String idempotencyKey;

    /** 订单号 */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 商品ID */
    private Long productId;

    /** 秒杀活动ID */
    private Long seckillActivityId;

    /** 购买数量 */
    private Integer quantity;

    /** 订单金额 */
    private BigDecimal totalAmount;

    /** 创建时间戳 */
    private Long createTime;
}
