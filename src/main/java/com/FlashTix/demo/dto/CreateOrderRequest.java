package com.FlashTix.demo.dto;

public class CreateOrderRequest {

    private String eventId;
    private String userId;
    private int ticketCount = 1;

    public CreateOrderRequest() {
    }

    public CreateOrderRequest(String eventId, String userId, int ticketCount) {
        this.eventId = eventId;
        this.userId = userId;
        this.ticketCount = ticketCount;
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
        return ticketCount > 0 ? ticketCount : 1;
    }

    public void setTicketCount(int ticketCount) {
        this.ticketCount = ticketCount;
    }
}
