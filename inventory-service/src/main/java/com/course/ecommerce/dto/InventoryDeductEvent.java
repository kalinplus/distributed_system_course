package com.course.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 库存扣减事件DTO
 * 用于Kafka消息传递，支付成功后触发库存同步
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDeductEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    private String orderNo;

    /** 商品ID */
    private Long productId;

    /** 扣减数量 */
    private Integer quantity;

    /** 时间戳 */
    private Long timestamp;
}
