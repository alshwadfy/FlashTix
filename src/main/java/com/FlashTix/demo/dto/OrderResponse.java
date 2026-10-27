package com.FlashTix.demo.dto;

import com.FlashTix.demo.model.OrderStatus;
import java.time.Instant;

public class OrderResponse {

    private String orderId;
    private String eventId;
    private String userId;
    private int ticketCount;
    private OrderStatus status;
    private String idempotencyKey;
    private Instant createdAt;
    private boolean duplicate;

    public OrderResponse() {
    }

    public OrderResponse(String orderId, String eventId, String userId, int ticketCount, OrderStatus status, String idempotencyKey, Instant createdAt, boolean duplicate) {
        this.orderId = orderId;
        this.eventId = eventId;
        this.userId = userId;
        this.ticketCount = ticketCount;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.duplicate = duplicate;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getTicketCount() {
        return ticketCount;
    }

    public void setTicketCount(int ticketCount) {
        this.ticketCount = ticketCount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }
}
