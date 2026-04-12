package com.CS335_Project3.api_gateway.ratelimiter;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class FixedWindowRateLimiterStrategy implements RateLimiterStrategy {

    /*
        Fixed window rate limiter.

        Concept:
        Each client has a counter for the current time window.

        Requests are counted inside that fixed window.
        When the window expires, the key is removed and a new window starts.

        This version stores the counter in Redis so it works across
        multiple instances (e.g. Docker containers).
    */

    /*
        Window size is now loaded from application.properties.

        This makes it easier to adjust for demo/testing without changing code.
    */
    @Value("${rate-limit.fixed.window-ms:10000}")
    private long windowSizeMs;

    /*
        TTL for Redis key is also loaded from application.properties.

        For fixed window, this should normally match the window length.
    */
    @Value("${rate-limit.redis.fixed.ttl-seconds:10}")
    private long ttlSeconds;

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

        /*
            First request in this window -> set expiry

            Once the window expires, Redis removes the key automatically
            and the next request starts a fresh counter.
        */
        if (count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
        }

        // Allow request if still under limit
        return count <= limit;
    }
}