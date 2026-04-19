package com.CS335_Project3.api_gateway.filter;

import java.util.concurrent.TimeUnit;
import com.CS335_Project3.api_gateway.config.AbuseDetectionConfig;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks failure responses per client using a Redis Sorted Set.
 */
@Component
public class Failure {

    private static final Logger log = LoggerFactory.getLogger(Failure.class);
    private static final String KEY_PREFIX = "failure:";

    private final RedisTemplate<String, String> redisTemplate;
    private final AbuseDetectionConfig config;

    public Failure(RedisTemplate<String, String> redisTemplate,
                   AbuseDetectionConfig config) {
        this.redisTemplate = redisTemplate;
        this.config        = config;
    }

    /**
     * Records a failure for {@code clientId} and returns true if the
     * failure threshold has been exceeded within the sliding window.
     *
     * @param clientId API key or IP address
     * @return true if the client should be blocked
     */
    public boolean recordAndCheck(String clientId) {
        try {
            String key        = KEY_PREFIX + clientId;
            long   now        = Instant.now().toEpochMilli();
            long   windowStart = now - (config.getFailure().getWindowSeconds() * 1000L);

            // Remove timestamps outside the sliding window
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

            // Add current timestamp — use nanoseconds as member for uniqueness
            String member = String.valueOf(System.nanoTime());
            redisTemplate.opsForZSet().add(key, member, now);

            // Set TTL so Redis auto-cleans idle keys
            redisTemplate.expire(key,
                    config.getFailure().getWindowSeconds() * 2L, TimeUnit.SECONDS);

            // Count failures in the current window
            Long count = redisTemplate.opsForZSet().zCard(key);
            int  total = count == null ? 0 : count.intValue();

            return total > config.getFailure().getMaxFailuresPerWindow();

        } catch (Exception e) {
            log.error("Redis unavailable — recordAndCheck failed for {}: {}", clientId, e.getMessage());
            return false; // fail open — don't block legitimate traffic if Redis is down
        }
    }

    /**
     * Returns the current failure count within the active window.
     * Useful for risk scoring and metrics.
     *
     * @param clientId API key or IP address
     */
    public int getFailureCount(String clientId) {
        try {
            String key        = KEY_PREFIX + clientId;
            long   now        = Instant.now().toEpochMilli();
            long   windowStart = now - (config.getFailure().getWindowSeconds() * 1000L);

            // Remove stale entries first
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

            Long count = redisTemplate.opsForZSet().zCard(key);
            return count == null ? 0 : count.intValue();

        } catch (Exception e) {
            log.error("Redis unavailable — getFailureCount failed for {}: {}", clientId, e.getMessage());
            return 0;
        }
    }

    /** Clears all failure records for a client. Used in tests. */
    public void reset(String clientId) {
        try {
            redisTemplate.delete(KEY_PREFIX + clientId);
        } catch (Exception e) {
            log.error("Redis unavailable — reset failed for {}: {}", clientId, e.getMessage());
        }
    }

    /** Clears all failure records. Used in tests. */
    public void reset() {
        try {
            var keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.error("Redis unavailable — reset failed: {}", e.getMessage());
        }
    }
}