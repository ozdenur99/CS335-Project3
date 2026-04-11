package com.CS335_Project3.api_gateway.ratelimiter;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class TokenBucketRateLimiterStrategy implements RateLimiterStrategy {

    /*
       Token bucket rate limiter.

       Concept:
       Each client has a bucket that contains tokens.
       every request they make uses up one token.
       Tokens are added back over time at a fixed refill rate
       If the bucket has no tokens left then the request is rejected
    */

    private static final String KEY_PREFIX = "rate_limit:token:";
    private static final String TOKENS_FIELD = "tokens";
    private static final String LAST_REFILL_FIELD = "lastRefillTime";

    /*
        TTL for Redis key is now loaded from application.properties.

        This makes it easier to adjust for demo/testing without changing code.
    */
    @Value("${rate-limit.redis.token.ttl-seconds:120}")
    private long ttlSeconds;

    private final StringRedisTemplate redisTemplate;

    // token refill rate was slowed to 5 tokens every 120 seconds to test logging to return 429
    private final double refillRate = 5.0 / 120000.0;

    @Autowired
    public TokenBucketRateLimiterStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isRequestAllowed(String clientId, int limit) {

        String key = KEY_PREFIX + clientId;
        long now = System.currentTimeMillis();

        Object tokensObj = redisTemplate.opsForHash().get(key, TOKENS_FIELD);
        Object lastRefillObj = redisTemplate.opsForHash().get(key, LAST_REFILL_FIELD);

        double tokens;
        long lastRefillTime;

        // If this is the user's first request, start with a full bucket
        if (tokensObj == null || lastRefillObj == null) {
            tokens = limit;
            lastRefillTime = now;
        } else {
            tokens = Double.parseDouble(tokensObj.toString());
            lastRefillTime = Long.parseLong(lastRefillObj.toString());
        }

        // Refill tokens based on time passed
        long timePassed = now - lastRefillTime;
        double tokensToAdd = timePassed * refillRate;
        tokens = Math.min(limit, tokens + tokensToAdd);

        // Update refill timestamp
        lastRefillTime = now;

        boolean allowed = false;

        // Consume one token if available
        if (tokens >= 1.0) {
            tokens -= 1.0;
            allowed = true;
        }

        // Save updated bucket state to Redis
        redisTemplate.opsForHash().put(key, TOKENS_FIELD, String.valueOf(tokens));
        redisTemplate.opsForHash().put(key, LAST_REFILL_FIELD, String.valueOf(lastRefillTime));

        // Remove inactive buckets automatically
        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));

        return allowed;
    }
}