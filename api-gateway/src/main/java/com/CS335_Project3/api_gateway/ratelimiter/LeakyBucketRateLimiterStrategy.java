package com.CS335_Project3.api_gateway.ratelimiter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class LeakyBucketRateLimiterStrategy implements RateLimiterStrategy {

    private static final String KEY_PREFIX = "rate_limit:leaky:";
    private static final long WINDOW_MS = 10_000L;
    private static final Duration KEY_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    public LeakyBucketRateLimiterStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public synchronized boolean isRequestAllowed(String clientId, int limit) {
        String key = KEY_PREFIX + clientId;
        long now = System.currentTimeMillis();

        String levelRaw = (String) redisTemplate.opsForHash().get(key, "level");
        String updatedRaw = (String) redisTemplate.opsForHash().get(key, "updatedAt");
        double currentLevel = levelRaw == null ? 0.0 : safeDouble(levelRaw);
        long lastUpdated = updatedRaw == null ? now : safeLong(updatedRaw, now);

        double leakRatePerMs = ((double) limit) / WINDOW_MS;
        long elapsedMs = Math.max(0L, now - lastUpdated);
        double leaked = elapsedMs * leakRatePerMs;
        double nextLevel = Math.max(0.0, currentLevel - leaked);

        if (nextLevel + 1.0 > limit) {
            redisTemplate.opsForHash().put(key, "level", String.valueOf(nextLevel));
            redisTemplate.opsForHash().put(key, "updatedAt", String.valueOf(now));
            redisTemplate.expire(key, KEY_TTL);
            return false;
        }

        redisTemplate.opsForHash().put(key, "level", String.valueOf(nextLevel + 1.0));
        redisTemplate.opsForHash().put(key, "updatedAt", String.valueOf(now));
        redisTemplate.expire(key, KEY_TTL);
        return true;
    }

    private static double safeDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private static long safeLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}

