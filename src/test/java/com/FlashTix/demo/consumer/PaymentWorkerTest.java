package com.FlashTix.demo.consumer;

import com.FlashTix.demo.dto.OrderProcessMessage;
import com.FlashTix.demo.exception.PaymentFailedException;
import com.FlashTix.demo.model.Order;
import com.FlashTix.demo.model.OrderStatus;
import com.FlashTix.demo.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentWorkerTest {

    @Mock
    private OrderRepository orderRepository;

    private PaymentWorker paymentWorker;

    @BeforeEach
    void setUp() {
        paymentWorker = new PaymentWorker(orderRepository);
    }

    @Test
    void testProcessPayment_Success_TransitionsToConfirmed() {
        OrderProcessMessage message = new OrderProcessMessage("ord-100", "evt-1", "user-success", 1, "idem-100");
        Order pendingOrder = new Order("ord-100", "evt-1", "user-success", 1, OrderStatus.PENDING, "idem-100", Instant.now());

        when(orderRepository.findById("ord-100")).thenReturn(Optional.of(pendingOrder));

        paymentWorker.processPayment(message);

        assertEquals(OrderStatus.CONFIRMED, pendingOrder.getStatus());
        verify(orderRepository, times(1)).save(pendingOrder);
    }

    @Test
    void testProcessPayment_SimulatedFailure_ThrowsException() {
        OrderProcessMessage message = new OrderProcessMessage("ord-200", "evt-1", "user-fail-99", 1, "idem-200");

        assertThrows(PaymentFailedException.class, () -> paymentWorker.processPayment(message));

        verify(orderRepository, never()).save(any());
    }
}
