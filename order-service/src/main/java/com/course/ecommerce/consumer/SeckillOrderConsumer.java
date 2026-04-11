package com.course.ecommerce.consumer;

import com.course.ecommerce.event.OrderCreateEvent;
import com.course.ecommerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单消费者
 * 监听seckill-order topic，异步处理订单创建
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final OrderService orderService;

    /**
     * 监听秒杀订单topic
     *
     * @param event 订单创建事件
     * @param acknowledgment 手动确认对象
     */
    @KafkaListener(topics = "seckill-order", containerFactory = "kafkaListenerContainerFactory")
    public void consume(OrderCreateEvent event, Acknowledgment acknowledgment) {
        log.info("Received seckill order event, orderNo: {}, idempotencyKey: {}",
                event.getOrderNo(), event.getIdempotencyKey());

        try {
            // 调用订单服务创建订单
            orderService.createOrderFromSeckill(event);

            // 手动确认消息
            acknowledgment.acknowledge();
            log.info("Successfully processed and acknowledged seckill order, orderNo: {}", event.getOrderNo());
        } catch (Exception e) {
            log.error("Failed to process seckill order, orderNo: {}, error: {}", event.getOrderNo(), e.getMessage(), e);

            // 异常处理：调用补偿服务（Part G实现）
            // 目前先记录错误，补偿服务将在后续实现
            handleCompensation(event, e);

            // 确认消息以避免阻塞，实际场景中可能需要发送到死信队列
            acknowledgment.acknowledge();
        }
    }

    /**
     * 处理补偿逻辑
     * TODO: Part G 实现补偿服务后替换为正式补偿逻辑
     *
     * @param event 订单创建事件
     * @param e 异常
     */
    private void handleCompensation(OrderCreateEvent event, Exception e) {
        log.warn("Compensation needed for orderNo: {}, idempotencyKey: {}. Error: {}",
                event.getOrderNo(), event.getIdempotencyKey(), e.getMessage());
        // Part G: 调用补偿服务，回滚库存等操作
        // compensationService.compensate(event);
    }
}
