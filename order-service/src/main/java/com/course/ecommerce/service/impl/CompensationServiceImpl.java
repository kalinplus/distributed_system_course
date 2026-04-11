package com.course.ecommerce.service.impl;

import com.course.ecommerce.event.OrderCreateEvent;
import com.course.ecommerce.service.CompensationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 补偿服务实现类
 * 使用RestTemplate调用库存服务进行库存回滚
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationServiceImpl implements CompensationService {

    private final RestTemplate restTemplate;

    @Value("${inventory.service.url:http://inventory-service:8084}")
    private String inventoryServiceUrl;

    @Override
    public void compensateOrderCreation(OrderCreateEvent event) {
        log.info("Starting compensation for order creation, orderNo: {}, productId: {}, quantity: {}",
                event.getOrderNo(), event.getProductId(), event.getQuantity());

        try {
            // 1. 调用库存服务回滚库存
            String url = String.format("%s/api/inventory/internal/rollback/%s/%s",
                    inventoryServiceUrl, event.getProductId(), event.getQuantity());

            log.info("Calling inventory service to rollback stock, url: {}", url);
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);

            if (response.getStatusCode().is2xxSuccessful() && "OK".equals(response.getBody())) {
                log.info("Stock rollback successful for orderNo: {}, productId: {}",
                        event.getOrderNo(), event.getProductId());
            } else {
                log.warn("Stock rollback returned unexpected response, orderNo: {}, status: {}, body: {}",
                        event.getOrderNo(), response.getStatusCode(), response.getBody());
            }

            // 2. 不清理幂等性Key，让用户保持"已参与"状态，避免重复扣库存
            log.info("Keeping idempotency key to prevent duplicate stock deduction, idempotencyKey: {}",
                    event.getIdempotencyKey());

        } catch (RestClientException e) {
            log.error("Failed to rollback stock for orderNo: {}, productId: {}, error: {}",
                    event.getOrderNo(), event.getProductId(), e.getMessage(), e);
            // 记录失败，但不抛出异常，避免影响消息确认
        }

        log.info("Compensation completed for orderNo: {}", event.getOrderNo());
    }
}
