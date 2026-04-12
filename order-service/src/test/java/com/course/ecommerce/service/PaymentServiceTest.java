package com.course.ecommerce.service;

import com.course.ecommerce.dto.InventoryDeductEvent;
import com.course.ecommerce.dto.PaymentCallbackRequest;
import com.course.ecommerce.entity.Order;
import com.course.ecommerce.mapper.OrderMapper;
import com.course.ecommerce.producer.InventoryDeductProducer;
import com.course.ecommerce.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 支付服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private InventoryDeductProducer inventoryDeductProducer;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private PaymentCallbackRequest successRequest;
    private PaymentCallbackRequest failedRequest;
    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        // 模拟Redis操作
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // 支付成功请求
        successRequest = new PaymentCallbackRequest();
        successRequest.setOrderNo("ORDER202404120001");
        successRequest.setPaymentNo("PAY202404120001");
        successRequest.setStatus("SUCCESS");
        successRequest.setAmount(new BigDecimal("8999.00"));
        successRequest.setPayTime(System.currentTimeMillis());

        // 支付失败请求
        failedRequest = new PaymentCallbackRequest();
        failedRequest.setOrderNo("ORDER202404120001");
        failedRequest.setPaymentNo("PAY202404120001");
        failedRequest.setStatus("FAILED");
        failedRequest.setAmount(new BigDecimal("8999.00"));

        // PENDING状态订单
        pendingOrder = new Order();
        pendingOrder.setId(1L);
        pendingOrder.setOrderNo("ORDER202404120001");
        pendingOrder.setUserId(1L);
        pendingOrder.setProductId(1L);
        pendingOrder.setQuantity(1);
        pendingOrder.setTotalAmount(new BigDecimal("8999.00"));
        pendingOrder.setStatus("PENDING");
        pendingOrder.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void handlePaymentCallback_Success() {
        // Given
        when(valueOperations.setIfAbsent(eq("payment:PAY202404120001"), any(), any(Duration.class)))
                .thenReturn(true);
        when(orderMapper.selectOne(any())).thenReturn(pendingOrder);
        when(orderMapper.update(any(), any())).thenReturn(1);

        // When - 不应抛出异常
        assertDoesNotThrow(() -> paymentService.handlePaymentCallback(successRequest));

        // Then
        verify(inventoryDeductProducer).sendInventoryDeductEvent(any(InventoryDeductEvent.class));
    }

    @Test
    void handlePaymentCallback_DuplicatePayment() {
        // Given - 幂等性检查返回false，表示已处理过
        when(valueOperations.setIfAbsent(eq("payment:PAY202404120001"), any(), any(Duration.class)))
                .thenReturn(false);

        // When - 不应抛出异常，直接返回
        assertDoesNotThrow(() -> paymentService.handlePaymentCallback(successRequest));

        // Then - 不查询订单，不发送库存扣减消息
        verify(orderMapper, never()).selectOne(any());
        verify(inventoryDeductProducer, never()).sendInventoryDeductEvent(any());
    }

    @Test
    void handlePaymentCallback_OrderNotFound() {
        // Given
        when(valueOperations.setIfAbsent(eq("payment:PAY202404120001"), any(), any(Duration.class)))
                .thenReturn(true);
        when(orderMapper.selectOne(any())).thenReturn(null);

        // When - 不应抛出异常，只是记录日志
        assertDoesNotThrow(() -> paymentService.handlePaymentCallback(successRequest));

        // Then - 不更新订单，不发送库存扣减消息
        verify(orderMapper, never()).update(any(), any());
        verify(inventoryDeductProducer, never()).sendInventoryDeductEvent(any());
    }

    @Test
    void handlePaymentCallback_OrderNotPending() {
        // Given
        Order paidOrder = new Order();
        paidOrder.setOrderNo("ORDER202404120001");
        paidOrder.setStatus("PAID");

        when(valueOperations.setIfAbsent(eq("payment:PAY202404120001"), any(), any(Duration.class)))
                .thenReturn(true);
        when(orderMapper.selectOne(any())).thenReturn(paidOrder);

        // When - 不应抛出异常
        assertDoesNotThrow(() -> paymentService.handlePaymentCallback(successRequest));

        // Then - 不更新订单，不发送库存扣减消息
        verify(orderMapper, never()).update(any(), any());
        verify(inventoryDeductProducer, never()).sendInventoryDeductEvent(any());
    }

    @Test
    void handlePaymentCallback_FailedStatus() {
        // Given
        when(valueOperations.setIfAbsent(eq("payment:PAY202404120001"), any(), any(Duration.class)))
                .thenReturn(true);
        when(orderMapper.selectOne(any())).thenReturn(pendingOrder);
        when(orderMapper.update(any(), any())).thenReturn(1);

        // When - 支付失败，不应抛出异常
        assertDoesNotThrow(() -> paymentService.handlePaymentCallback(failedRequest));

        // Then - 支付失败不发送库存扣减消息
        verify(inventoryDeductProducer, never()).sendInventoryDeductEvent(any());
    }

    @Test
    void handlePaymentCallback_UpdateFailed() {
        // Given
        when(valueOperations.setIfAbsent(eq("payment:PAY202404120001"), any(), any(Duration.class)))
                .thenReturn(true);
        when(orderMapper.selectOne(any())).thenReturn(pendingOrder);
        when(orderMapper.update(any(), any())).thenReturn(0); // 更新失败

        // When - 不应抛出异常
        assertDoesNotThrow(() -> paymentService.handlePaymentCallback(successRequest));

        // Then - 更新失败不发送库存扣减消息
        verify(inventoryDeductProducer, never()).sendInventoryDeductEvent(any());
    }
}
