package com.course.ecommerce.producer;

import com.course.ecommerce.dto.InventoryDeductEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 库存扣减生产者
 * 用于发送库存扣减消息到Kafka，支付成功后触发
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryDeductProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.inventory-deduct:inventory-deduct}")
    private String topicName;

    /**
     * 发送库存扣减事件
     *
     * @param event 库存扣减事件
     * @return CompletableFuture<SendResult<String, Object>> 发送结果
     */
    public CompletableFuture<SendResult<String, Object>> sendInventoryDeductEvent(InventoryDeductEvent event) {
        String key = event.getOrderNo();
        log.info("Sending inventory deduct event to Kafka, topic: {}, key: {}, orderNo: {}, productId: {}, quantity: {}",
                topicName, key, event.getOrderNo(), event.getProductId(), event.getQuantity());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topicName, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Inventory deduct event sent successfully, topic: {}, partition: {}, offset: {}, key: {}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        key);
            } else {
                log.error("Failed to send inventory deduct event, topic: {}, key: {}, error: {}",
                        topicName, key, ex.getMessage(), ex);
            }
        });

        return future;
    }
}
