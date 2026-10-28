package com.FlashTix.demo.config;

import com.FlashTix.demo.model.Event;
import com.FlashTix.demo.repository.EventRepository;
import com.FlashTix.demo.service.RedisInventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final EventRepository eventRepository;
    private final RedisInventoryService redisInventoryService;

    public DataInitializer(EventRepository eventRepository, RedisInventoryService redisInventoryService) {
        this.eventRepository = eventRepository;
        this.redisInventoryService = redisInventoryService;
    }

    @Override
    public void run(String... args) {
        String eventId = "evt-1";
        int initialStock = 100;

        if (!eventRepository.existsById(eventId)) {
            try {
                Event event = new Event(eventId, "Coldplay World Tour", initialStock, initialStock);
                eventRepository.save(event);
                log.info("Initialized event {} in PostgreSQL", eventId);
            } catch (Exception e) {
                log.info("Event {} was initialized by another node: {}", eventId, e.getMessage());
            }
        }

        // Warm up Redis cache with stock from DB
        redisInventoryService.setStock(eventId, initialStock);
        log.info("Warmed up Redis inventory for event {} with {} tickets", eventId, initialStock);
    }
}
