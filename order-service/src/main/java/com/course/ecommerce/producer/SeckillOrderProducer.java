package com.course.ecommerce.producer;

import com.course.ecommerce.event.OrderCreateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 秒杀订单生产者
 * 用于发送秒杀订单创建消息到Kafka
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.seckill-order:seckill-order}")
    private String topicName;

    /**
     * 发送订单创建事件
     *
     * @param event 订单创建事件
     * @return CompletableFuture<SendResult<String, Object>> 发送结果
     */
    public CompletableFuture<SendResult<String, Object>> sendOrderCreateEvent(OrderCreateEvent event) {
        String key = event.getIdempotencyKey();
        log.info("Sending order create event to Kafka, topic: {}, key: {}, orderNo: {}",
                topicName, key, event.getOrderNo());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topicName, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Order create event sent successfully, topic: {}, partition: {}, offset: {}, key: {}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        key);
            } else {
                log.error("Failed to send order create event, topic: {}, key: {}, error: {}",
                        topicName, key, ex.getMessage(), ex);
            }
        });

        return future;
    }
}
