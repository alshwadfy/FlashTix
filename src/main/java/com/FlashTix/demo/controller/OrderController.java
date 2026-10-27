package com.FlashTix.demo.controller;

import com.FlashTix.demo.dto.CreateOrderRequest;
import com.FlashTix.demo.dto.OrderResponse;
import com.FlashTix.demo.service.OrderService;
import com.FlashTix.demo.service.RedisInventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService orderService;
    private final RedisInventoryService redisInventoryService;

    public OrderController(OrderService orderService, RedisInventoryService redisInventoryService) {
        this.orderService = orderService;
        this.redisInventoryService = redisInventoryService;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateOrderRequest request
    ) {
        OrderResponse response = orderService.createOrder(idempotencyKey, request);
        if (response.isDuplicate()) {
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/events/{eventId}/stock")
    public ResponseEntity<Map<String, Object>> getStock(@PathVariable String eventId) {
        int stock = redisInventoryService.getStock(eventId);
        return ResponseEntity.ok(Map.of(
                "eventId", eventId,
                "availableStock", stock
        ));
    }
}
