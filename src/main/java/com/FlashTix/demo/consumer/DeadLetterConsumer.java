package com.FlashTix.demo.consumer;

import com.FlashTix.demo.config.RabbitMQConfig;
import com.FlashTix.demo.dto.OrderProcessMessage;
import com.FlashTix.demo.model.OrderStatus;
import com.FlashTix.demo.repository.OrderRepository;
import com.FlashTix.demo.service.RedisInventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);

    private final OrderRepository orderRepository;
    private final RedisInventoryService redisInventoryService;

    public DeadLetterConsumer(OrderRepository orderRepository, RedisInventoryService redisInventoryService) {
        this.orderRepository = orderRepository;
        this.redisInventoryService = redisInventoryService;
    }

    @RabbitListener(queues = RabbitMQConfig.DLQ_QUEUE_NAME)
    public void processDeadLetter(OrderProcessMessage message) {
        log.error("💀 [DLQ CONSUMER] Order {} exceeded all retry attempts and arrived in DLQ! Initiating SAGA compensation...",
                message.orderId());

        // 1. Mark order as FAILED in PostgreSQL
        orderRepository.findById(message.orderId()).ifPresentOrElse(order -> {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            log.info("[DLQ CONSUMER] Order {} status transitioned to FAILED in database.", message.orderId());
        }, () -> log.error("[DLQ CONSUMER] Order {} not found in database!", message.orderId()));

        // 2. SAGA Compensation: Restore stock in Redis
        long restoredStock = redisInventoryService.compensateStock(message.eventId(), message.ticketCount());
        log.info("🔄 [DLQ CONSUMER] SAGA Compensation complete: Restored {} ticket(s) to event {}. New Redis stock: {}",
                message.ticketCount(), message.eventId(), restoredStock);

        // 3. Release idempotency lock in Redis so user or retry can re-attempt if necessary
        redisInventoryService.releaseIdempotencyLock(message.idempotencyKey());
    }
}
