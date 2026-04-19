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
       Every request they make uses up one token.
       Tokens are added back over time at a fixed refill rate.
       If the bucket has no tokens left then the request is rejected.
    */

    private static final String KEY_PREFIX = "rate_limit:token:";
    private static final String TOKENS_FIELD = "tokens";
    private static final String LAST_REFILL_FIELD = "lastRefillTime";

    /*
        TTL for Redis key is loaded from application.properties.

        This makes it easier to adjust for demo/testing without changing code.
    */
    @Value("${rate-limit.redis.token.ttl-seconds:120}")
    private long ttlSeconds;

    /*
        Token refill behaviour is loaded from application.properties.

        refillTokens     = how many tokens are restored
        refillPeriodMs   = over what time period they are restored

        Example:
        5 tokens every 120000 ms = 5 tokens every 120 seconds
    */
    @Value("${rate-limit.token.refill-tokens:5}")
    private double refillTokens;

    @Value("${rate-limit.token.refill-period-ms:120000}")
    private long refillPeriodMs;

    private final StringRedisTemplate redisTemplate;

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

        // Calculate refill rate from configuration
        double refillRate = refillTokens / refillPeriodMs;

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