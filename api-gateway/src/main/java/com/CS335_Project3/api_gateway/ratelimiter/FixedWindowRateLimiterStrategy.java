package com.CS335_Project3.api_gateway.ratelimiter;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class FixedWindowRateLimiterStrategy implements RateLimiterStrategy {

    // Window size = 10 seconds
    private static final long windowSizeMs = 10000;

    // TTL used by Redis (same as window size)
    private static final Duration windowTTL = Duration.ofMillis(windowSizeMs);

    // Prefix for Redis keys (one key per client)
    private static final String KEY_PREFIX = "rate_limit:fixed:";

    // Redis template used to interact with Redis
    private final StringRedisTemplate redisTemplate;

    /*
        Main constructor used by Spring Boot

        Spring injects Redis here when the app starts.
    */
    @Autowired
    public FixedWindowRateLimiterStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isRequestAllowed(String clientId, int limit) {

        // Create unique key per client
        String key = KEY_PREFIX + clientId;

        // Increment request count in Redis
        Long count = redisTemplate.opsForValue().increment(key);

        // Safety check (should not normally happen)
        if (count == null) {
            return false;
        }

        // First request in this window -> set expiry
        if (count == 1L) {
            redisTemplate.expire(key, windowTTL);
        }

        // Allow request if still under limit
        return count <= limit;
    }
}