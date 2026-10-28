package com.FlashTix.demo.service;

import com.FlashTix.demo.config.RabbitMQConfig;
import com.FlashTix.demo.dto.CreateOrderRequest;
import com.FlashTix.demo.dto.OrderProcessMessage;
import com.FlashTix.demo.dto.OrderResponse;
import com.FlashTix.demo.exception.SoldOutException;
import com.FlashTix.demo.model.Order;
import com.FlashTix.demo.model.OrderStatus;
import com.FlashTix.demo.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RedisInventoryService redisInventoryService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, redisInventoryService, rabbitTemplate);
    }

    @Test
    void testCreateOrder_HappyPath_PublishesToRabbitMQ() {
        String key = "idem-key-1";
        CreateOrderRequest request = new CreateOrderRequest("evt-1", "user-1", 1);

        when(orderRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(redisInventoryService.tryAcquireIdempotencyLock(key)).thenReturn(true);
        when(redisInventoryService.tryAcquireStock("evt-1", 1)).thenReturn(99L);

        Order savedOrder = new Order("ord-1", "evt-1", "user-1", 1, OrderStatus.PENDING, key, Instant.now());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderResponse response = orderService.createOrder(key, request);

        assertNotNull(response);
        assertEquals("ord-1", response.getOrderId());
        assertEquals(OrderStatus.PENDING, response.getStatus());

        // Verify message published to RabbitMQ
        ArgumentCaptor<OrderProcessMessage> captor = ArgumentCaptor.forClass(OrderProcessMessage.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.ROUTING_KEY),
                captor.capture()
        );

        OrderProcessMessage sent = captor.getValue();
        assertEquals("ord-1", sent.orderId());
        assertEquals("evt-1", sent.eventId());
        assertEquals("user-1", sent.userId());
        assertEquals(1, sent.ticketCount());
    }

    @Test
    void testCreateOrder_SoldOut_DoesNotPublishToRabbitMQ() {
        String key = "idem-key-2";
        CreateOrderRequest request = new CreateOrderRequest("evt-1", "user-2", 1);

        when(orderRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(redisInventoryService.tryAcquireIdempotencyLock(key)).thenReturn(true);
        when(redisInventoryService.tryAcquireStock("evt-1", 1)).thenReturn(-1L);

        assertThrows(SoldOutException.class, () -> orderService.createOrder(key, request));

        verify(redisInventoryService, times(1)).releaseIdempotencyLock(key);
        verifyNoInteractions(rabbitTemplate);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void testCreateOrder_RabbitMQFails_CompensatesRedisStock() {
        String key = "idem-key-3";
        CreateOrderRequest request = new CreateOrderRequest("evt-1", "user-3", 1);

        when(orderRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(redisInventoryService.tryAcquireIdempotencyLock(key)).thenReturn(true);
        when(redisInventoryService.tryAcquireStock("evt-1", 1)).thenReturn(98L);

        Order savedOrder = new Order("ord-3", "evt-1", "user-3", 1, OrderStatus.PENDING, key, Instant.now());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        doThrow(new RuntimeException("RabbitMQ connection lost"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(OrderProcessMessage.class));

        assertThrows(RuntimeException.class, () -> orderService.createOrder(key, request));

        // Compensation verification: Redis stock incremented back & lock released
        verify(redisInventoryService, times(1)).compensateStock("evt-1", 1);
        verify(redisInventoryService, times(1)).releaseIdempotencyLock(key);
    }
}
