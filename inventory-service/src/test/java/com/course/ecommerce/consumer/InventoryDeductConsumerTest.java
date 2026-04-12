package com.course.ecommerce.consumer;

import com.course.ecommerce.dto.InventoryDeductEvent;
import com.course.ecommerce.entity.Inventory;
import com.course.ecommerce.mapper.InventoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 库存扣减消费者单元测试
 */
@ExtendWith(MockitoExtension.class)
class InventoryDeductConsumerTest {

    @Mock
    private InventoryMapper inventoryMapper;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private InventoryDeductConsumer inventoryDeductConsumer;

    private InventoryDeductEvent event;

    @BeforeEach
    void setUp() {
        event = InventoryDeductEvent.builder()
                .orderNo("ORDER202404120001")
                .productId(1L)
                .quantity(1)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @Test
    void consume_Success() {
        // Given
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        // When
        inventoryDeductConsumer.consume(event, acknowledgment);

        // Then
        verify(inventoryMapper).update(any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_InsufficientStock() {
        // Given - 库存不足，更新返回0
        when(inventoryMapper.update(any(), any())).thenReturn(0);

        // When & Then - 应该抛出异常，让Kafka重试
        assertThrows(RuntimeException.class, () -> {
            inventoryDeductConsumer.consume(event, acknowledgment);
        });

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consume_DatabaseError() {
        // Given - 数据库错误
        when(inventoryMapper.update(any(), any()))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then - 应该抛出异常，让Kafka重试
        assertThrows(RuntimeException.class, () -> {
            inventoryDeductConsumer.consume(event, acknowledgment);
        });

        verify(acknowledgment, never()).acknowledge();
    }
}
