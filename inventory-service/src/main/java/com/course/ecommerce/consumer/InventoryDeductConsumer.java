package com.course.ecommerce.consumer;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.course.ecommerce.dto.InventoryDeductEvent;
import com.course.ecommerce.entity.Inventory;
import com.course.ecommerce.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 库存扣减消费者
 * 监听inventory-deduct topic，使用乐观锁扣减MySQL库存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryDeductConsumer {

    private final InventoryMapper inventoryMapper;

    /**
     * 监听库存扣减topic
     *
     * @param event 库存扣减事件
     * @param acknowledgment 手动确认对象
     */
    @KafkaListener(topics = "inventory-deduct", containerFactory = "kafkaListenerContainerFactory")
    public void consume(InventoryDeductEvent event, Acknowledgment acknowledgment) {
        log.info("Received inventory deduct event, orderNo: {}, productId: {}, quantity: {}",
                event.getOrderNo(), event.getProductId(), event.getQuantity());

        try {
            // 使用乐观锁扣减库存
            // SQL: UPDATE t_inventory SET stock = stock - ?, version = version + 1
            //      WHERE product_id = ? AND stock >= ?
            LambdaUpdateWrapper<Inventory> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Inventory::getProductId, event.getProductId())
                    .ge(Inventory::getStock, event.getQuantity())  // 确保库存充足
                    .setSql("stock = stock - " + event.getQuantity());  // 扣减库存，version由@Version自动处理

            int updated = inventoryMapper.update(updateWrapper);

            if (updated == 0) {
                // 乐观锁更新失败：库存不足或并发冲突
                log.error("Inventory deduct failed, insufficient stock or concurrent conflict, " +
                        "orderNo: {}, productId: {}, quantity: {}",
                        event.getOrderNo(), event.getProductId(), event.getQuantity());
                throw new RuntimeException("库存扣减失败，库存不足或并发冲突: productId=" + event.getProductId());
            }

            // 手动确认消息
            acknowledgment.acknowledge();
            log.info("Inventory deduct processed and acknowledged successfully, " +
                    "orderNo: {}, productId: {}, quantity: {}",
                    event.getOrderNo(), event.getProductId(), event.getQuantity());

        } catch (Exception e) {
            log.error("Failed to process inventory deduct, orderNo: {}, error: {}",
                    event.getOrderNo(), e.getMessage(), e);
            // 抛出异常让Kafka重试
            throw e;
        }
    }
}
