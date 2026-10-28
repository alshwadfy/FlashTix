package com.FlashTix.demo.consumer;

import com.FlashTix.demo.dto.OrderProcessMessage;
import com.FlashTix.demo.model.Order;
import com.FlashTix.demo.model.OrderStatus;
import com.FlashTix.demo.repository.OrderRepository;
import com.FlashTix.demo.service.RedisInventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeadLetterConsumerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RedisInventoryService redisInventoryService;

    private DeadLetterConsumer deadLetterConsumer;

    @BeforeEach
    void setUp() {
        deadLetterConsumer = new DeadLetterConsumer(orderRepository, redisInventoryService);
    }

    @Test
    void testProcessDeadLetter_TransitionsToFailed_AndCompensatesRedisStock() {
        OrderProcessMessage message = new OrderProcessMessage("ord-dead-1", "evt-1", "user-fail-1", 2, "idem-dead-1");
        Order pendingOrder = new Order("ord-dead-1", "evt-1", "user-fail-1", 2, OrderStatus.PENDING, "idem-dead-1", Instant.now());

        when(orderRepository.findById("ord-dead-1")).thenReturn(Optional.of(pendingOrder));
        when(redisInventoryService.compensateStock("evt-1", 2)).thenReturn(10L);

        deadLetterConsumer.processDeadLetter(message);

        // 1. Order status is FAILED
        assertEquals(OrderStatus.FAILED, pendingOrder.getStatus());
        verify(orderRepository, times(1)).save(pendingOrder);

        // 2. Stock compensated in Redis
        verify(redisInventoryService, times(1)).compensateStock("evt-1", 2);

        // 3. Idempotency lock released
        verify(redisInventoryService, times(1)).releaseIdempotencyLock("idem-dead-1");
    }
}
