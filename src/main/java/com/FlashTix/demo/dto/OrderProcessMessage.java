package com.FlashTix.demo.dto;

import java.io.Serializable;

public record OrderProcessMessage(
        String orderId,
        String eventId,
        String userId,
        int ticketCount,
        String idempotencyKey
) implements Serializable {}
