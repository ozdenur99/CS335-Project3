package com.CS335_Project3.api_gateway.filter;

import com.CS335_Project3.api_gateway.config.AbuseDetectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed IP blocklist shared across all gateway instances.
 *
 * WHY REDIS:
 * Previously BlockedIps used an in-memory ConcurrentHashMap.
 * This meant gateway-1 banning an IP had no effect on gateway-2 —
 * a blocked client could simply switch ports and get through.
 * Now both gateways read and write the same Redis Set, so a ban
 * on either gateway is instantly visible to the other.
 *
 * TTL:
 * Each blocked entry is stored with a Redis TTL equal to
 * abuse.blockDurationSeconds (default 5 minutes). Redis automatically
 * removes expired keys — no manual cleanup needed.
 *
 * KEY PATTERN:
 * blocked:{clientId}  — individual key per blocked client
 * This lets us use Redis TTL per entry rather than a shared Set,
 * which means each client's block expires independently.
 */
@Component
public class BlockedIps {

    private static final Logger log = LoggerFactory.getLogger(BlockedIps.class);
    private static final String KEY_PREFIX = "blocked:";

    private final RedisTemplate<String, String> redisTemplate;
    private final AbuseDetectionConfig config;

    public BlockedIps(RedisTemplate<String, String> redisTemplate,
                      AbuseDetectionConfig config) {
        this.redisTemplate = redisTemplate;
        this.config = config;
    }

    /**
     * Adds a client to the Redis blocklist with TTL.
     * Block expires automatically after abuse.blockDurationSeconds.
     *
     * @param clientId API key or IP address
     */
    public void block(String clientId) {
        try {
            String key = KEY_PREFIX + clientId;
            redisTemplate.opsForValue().set(key, "blocked",
                    config.getBlockDurationSeconds(), TimeUnit.SECONDS);
            log.warn("Blocked clientId={} for {}s", clientId, config.getBlockDurationSeconds());
        } catch (Exception e) {
            log.error("Redis unavailable — block not persisted for {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Manually removes a client from the blocklist.
     * Called by admin or after review.
     *
     * @param clientId API key or IP address
     */
    public void unblock(String clientId) {
        try {
            redisTemplate.delete(KEY_PREFIX + clientId);
            log.info("Unblocked clientId={}", clientId);
        } catch (Exception e) {
            log.error("Redis unavailable — unblock failed for {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Returns true if the client is currently blocked.
     * Redis TTL handles expiry automatically — no manual check needed.
     *
     * Falls back to false if Redis is unavailable (fail open).
     *
     * @param clientId API key or IP address
     */
    public boolean isBlocked(String clientId) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.hasKey(KEY_PREFIX + clientId));
        } catch (Exception e) {
            log.error("Redis unavailable — isBlocked check failed for {}: {}", clientId, e.getMessage());
            return false; // fail open — don't block legitimate traffic if Redis is down
        }
    }

    /**
     * Returns a snapshot of all currently blocked clients.
     * Useful for the metrics endpoint.
     */
    public Set<String> getBlockedClients() {
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) return Collections.emptySet();
            // Strip the "blocked:" prefix to return just the clientId
            return keys.stream()
                    .map(k -> k.substring(KEY_PREFIX.length()))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (Exception e) {
            log.error("Redis unavailable — getBlockedClients failed: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /** Clears all blocked clients. Used in tests only. */
    public void reset() {
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.error("Redis unavailable — reset failed: {}", e.getMessage());
        }
    }
}