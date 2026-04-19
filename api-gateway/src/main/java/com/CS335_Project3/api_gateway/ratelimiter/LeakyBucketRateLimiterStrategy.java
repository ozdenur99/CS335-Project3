package com.CS335_Project3.api_gateway.ratelimiter;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class LeakyBucketRateLimiterStrategy implements RateLimiterStrategy {

    /*
       Leaky bucket rate limiter.

       Concept:
       Each client has a bucket that fills when requests arrive.
       The bucket leaks at a constant fixed rate over time.

       If a new request would overflow the bucket,
       the request is rejected.

       This smooths traffic and prevents large bursts.
    */

    private static final String KEY_PREFIX = "rate_limit:leaky:";
    private static final String WATER_LEVEL_FIELD = "waterLevel";
    private static final String LAST_LEAK_FIELD = "lastLeakTime";

    /*
        TTL for Redis key is loaded from application.properties.

        This makes it easier to adjust for demo/testing without changing code.
    */
    @Value("${rate-limit.redis.leaky.ttl-seconds:300}")
    private long ttlSeconds;

    /*
        Leak behaviour is loaded from application.properties.

        leakUnits      = how many units leak out
        leakPeriodMs   = over what time period they leak out

        Example:
        3 units every 10000 ms = 3 units every 10 seconds
    */
    @Value("${rate-limit.leaky.leak-units:3}")
    private double leakUnits;

    @Value("${rate-limit.leaky.leak-period-ms:10000}")
    private long leakPeriodMs;

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public LeakyBucketRateLimiterStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isRequestAllowed(String clientId, int limit) {

        String key = KEY_PREFIX + clientId;
        long now = System.currentTimeMillis();

        Object waterObj = redisTemplate.opsForHash().get(key, WATER_LEVEL_FIELD);
        Object lastLeakObj = redisTemplate.opsForHash().get(key, LAST_LEAK_FIELD);

        double waterLevel;
        long lastLeakTime;

        // Calculate leak rate from configuration
        double leakRate = leakUnits / leakPeriodMs;

        // If this is the client's first request, start with an empty bucket
        if (waterObj == null || lastLeakObj == null) {
            waterLevel = 0.0;
            lastLeakTime = now;
        } else {
            waterLevel = Double.parseDouble(waterObj.toString());
            lastLeakTime = Long.parseLong(lastLeakObj.toString());
        }

        // Leak water based on elapsed time
        long timePassed = now - lastLeakTime;
        double leakedAmount = timePassed * leakRate;
        waterLevel = Math.max(0.0, waterLevel - leakedAmount);

        // Update leak timestamp
        lastLeakTime = now;

        boolean allowed = false;

        // Allow request if adding one more unit would not overflow the bucket
        if (waterLevel + 1.0 <= limit) {
            waterLevel += 1.0;
            allowed = true;
        }

        // Save updated state to Redis
        redisTemplate.opsForHash().put(key, WATER_LEVEL_FIELD, String.valueOf(waterLevel));
        redisTemplate.opsForHash().put(key, LAST_LEAK_FIELD, String.valueOf(lastLeakTime));

        // Remove inactive buckets automatically
        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));

        return allowed;
    }
}