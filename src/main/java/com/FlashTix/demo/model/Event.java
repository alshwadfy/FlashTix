package com.FlashTix.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "events")
public class Event {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(name = "total_tickets", nullable = false)
    private int totalTickets;

    @Column(name = "available_stock", nullable = false)
    private int availableStock;

    public Event() {
    }

    public Event(String id, String title, int totalTickets, int availableStock) {
        this.id = id;
        this.title = title;
        this.totalTickets = totalTickets;
        this.availableStock = availableStock;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getTotalTickets() {
        return totalTickets;
    }

    public void setTotalTickets(int totalTickets) {
        this.totalTickets = totalTickets;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(int availableStock) {
        this.availableStock = availableStock;
    }
}
