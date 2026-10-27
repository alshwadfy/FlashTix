package com.FlashTix.demo.service;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Service
public class RedisInventoryService {

    private final StringRedisTemplate redisTemplate;
    private DefaultRedisScript<Long> decrementScript;

    public RedisInventoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        decrementScript = new DefaultRedisScript<>();
        decrementScript.setResultType(Long.class);
        decrementScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/decrement_stock.lua")));
    }

    private String getStockKey(String eventId) {
        return "event:" + eventId + ":stock";
    }

    private String getIdempotencyKey(String key) {
        return "idempotency:" + key;
    }

    /**
     * Seeds or overrides stock in Redis.
     */
    public void setStock(String eventId, int stock) {
        redisTemplate.opsForValue().set(getStockKey(eventId), String.valueOf(stock));
    }

    /**
     * Reads current stock from Redis.
     */
    public int getStock(String eventId) {
        String val = redisTemplate.opsForValue().get(getStockKey(eventId));
        return val != null ? Integer.parseInt(val) : 0;
    }

    /**
     * Atomically checks and decrements stock in Redis via Lua script.
     *
     * @return remaining stock (>= 0) on success, or -1 if out of stock / failed
     */
    public long tryAcquireStock(String eventId, int ticketCount) {
        List<String> keys = Collections.singletonList(getStockKey(eventId));
        Long result = redisTemplate.execute(decrementScript, keys, String.valueOf(ticketCount));
        return result != null ? result : -1;
    }

    /**
     * Compensates stock by incrementing Redis counter back up (e.g., if DB insert failed).
     */
    public void compensateStock(String eventId, int ticketCount) {
        redisTemplate.opsForValue().increment(getStockKey(eventId), ticketCount);
    }

    /**
     * Atomically sets an idempotency lock in Redis with a 5-minute TTL.
     * Returns true if this is the first time we see this key.
     * Returns false if duplicate / already being processed.
     */
    public boolean tryAcquireIdempotencyLock(String idempotencyKey) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(getIdempotencyKey(idempotencyKey), "PROCESSING", Duration.ofMinutes(5));
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * Releases the idempotency lock if order creation fails before DB commit,
     * allowing the client to safely retry.
     */
    public void releaseIdempotencyLock(String idempotencyKey) {
        redisTemplate.delete(getIdempotencyKey(idempotencyKey));
    }
}
