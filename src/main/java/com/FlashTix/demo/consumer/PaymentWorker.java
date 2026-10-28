package com.FlashTix.demo.consumer;

import com.FlashTix.demo.config.RabbitMQConfig;
import com.FlashTix.demo.dto.OrderProcessMessage;
import com.FlashTix.demo.exception.PaymentFailedException;
import com.FlashTix.demo.model.OrderStatus;
import com.FlashTix.demo.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentWorker.class);

    private final OrderRepository orderRepository;

    public PaymentWorker(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void processPayment(OrderProcessMessage message) {
        log.info("[PAYMENT WORKER] Received order {} for processing (user={}, tickets={})",
                message.orderId(), message.userId(), message.ticketCount());

        // Simulated payment failure: triggers retry with backoff, eventually routing to DLQ
        if (message.userId() != null && message.userId().contains("fail")) {
            log.warn("[PAYMENT WORKER] 💥 Payment simulation triggered failure for order {} (user={})! Throwing exception to trigger retry backoff...",
                    message.orderId(), message.userId());
            throw new PaymentFailedException("Payment gateway declined transaction for order " + message.orderId());
        }

        // Happy Path: Payment succeeds
        orderRepository.findById(message.orderId()).ifPresentOrElse(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.CONFIRMED);
                orderRepository.save(order);
                log.info("[PAYMENT WORKER] ✅ Order {} CONFIRMED successfully in database.", message.orderId());
            } else {
                log.info("[PAYMENT WORKER] Order {} already in state: {}", message.orderId(), order.getStatus());
            }
        }, () -> log.error("[PAYMENT WORKER] Order {} not found in database!", message.orderId()));
    }
}
