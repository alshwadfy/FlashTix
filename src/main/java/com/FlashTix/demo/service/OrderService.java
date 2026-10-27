package com.FlashTix.demo.service;

import com.FlashTix.demo.dto.CreateOrderRequest;
import com.FlashTix.demo.dto.OrderResponse;
import com.FlashTix.demo.exception.DuplicateInFlightException;
import com.FlashTix.demo.exception.SoldOutException;
import com.FlashTix.demo.model.Order;
import com.FlashTix.demo.model.OrderStatus;
import com.FlashTix.demo.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final RedisInventoryService redisInventoryService;

    public OrderService(OrderRepository orderRepository, RedisInventoryService redisInventoryService) {
        this.orderRepository = orderRepository;
        this.redisInventoryService = redisInventoryService;
    }

    public OrderResponse createOrder(String idempotencyKey, CreateOrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = UUID.randomUUID().toString();
        }

        // 1. Check if an order already exists with this idempotency key in DB (Stripe pattern)
        Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOrder.isPresent()) {
            Order o = existingOrder.get();
            return new OrderResponse(
                    o.getId(),
                    o.getEventId(),
                    o.getUserId(),
                    o.getTicketCount(),
                    o.getStatus(),
                    o.getIdempotencyKey(),
                    o.getCreatedAt(),
                    true // duplicate
            );
        }

        // 2. Acquire Redis in-flight lock to prevent simultaneous duplicate requests
        boolean lockAcquired = redisInventoryService.tryAcquireIdempotencyLock(idempotencyKey);
        if (!lockAcquired) {
            throw new DuplicateInFlightException("A request with idempotency key " + idempotencyKey + " is currently in flight.");
        }

        // 3. Redis Lua Gatekeeper: atomically check & decrement stock
        int count = request.getTicketCount();
        long remainingStock = redisInventoryService.tryAcquireStock(request.getEventId(), count);
        if (remainingStock < 0) {
            redisInventoryService.releaseIdempotencyLock(idempotencyKey);
            throw new SoldOutException("Tickets for event " + request.getEventId() + " are sold out!");
        }

        // 4. Save PENDING order in PostgreSQL (with compensation on failure)
        Order savedOrder;
        try {
            Order order = new Order(
                    UUID.randomUUID().toString(),
                    request.getEventId(),
                    request.getUserId(),
                    count,
                    OrderStatus.PENDING,
                    idempotencyKey,
                    Instant.now()
            );
            savedOrder = orderRepository.save(order);
        } catch (Exception ex) {
            // COMPENSATION: DB failed -> restore Redis stock and release idempotency lock
            redisInventoryService.compensateStock(request.getEventId(), count);
            redisInventoryService.releaseIdempotencyLock(idempotencyKey);
            throw ex;
        }

        return new OrderResponse(
                savedOrder.getId(),
                savedOrder.getEventId(),
                savedOrder.getUserId(),
                savedOrder.getTicketCount(),
                savedOrder.getStatus(),
                savedOrder.getIdempotencyKey(),
                savedOrder.getCreatedAt(),
                false // not duplicate
        );
    }
}
