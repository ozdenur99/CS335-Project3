package com.CS335_Project3.api_gateway.filter;

import com.CS335_Project3.api_gateway.config.AbuseDetectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes abuse ban events to a Redis Pub/Sub channel.
 *
 * WHY PUB/SUB:
 * When gateway-1 bans an IP, it writes to Redis (T6).
 * But gateway-2 only checks Redis on the NEXT request from that IP.
 * Pub/Sub makes gateway-2 aware of the ban INSTANTLY by pushing
 * a notification to all subscribers as soon as a ban happens.
 *
 * CHANNEL: abuse-events (configurable via abuse.redis.channel)
 *
 * MESSAGE FORMAT: "BLOCK:{clientId}" or "UNBLOCK:{clientId}"
 * This keeps messages simple and parseable by the subscriber.
 *
 * DEMO MOMENT:
 * Ban an IP on gateway-1 (port 8080) → gateway-2 (port 8082)
 * immediately starts blocking that IP without waiting for a request.
 */
@Component
public class AbuseEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AbuseEventPublisher.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final AbuseDetectionConfig config;

    public AbuseEventPublisher(RedisTemplate<String, String> redisTemplate,
                               AbuseDetectionConfig config) {
        this.redisTemplate = redisTemplate;
        this.config = config;
    }

    /**
     * Publishes a ban event to all gateway instances.
     * Called by AbuseFilter after adding a client to BlockedIps.
     *
     * @param clientId the API key or IP that was banned
     */
    public void publishBan(String clientId) {
        publish("BLOCK:" + clientId);
    }

    /**
     * Publishes an unban event to all gateway instances.
     * Called when an IP is manually unblocked.
     *
     * @param clientId the API key or IP that was unbanned
     */
    public void publishUnban(String clientId) {
        publish("UNBLOCK:" + clientId);
    }

    private void publish(String message) {
        try {
            redisTemplate.convertAndSend(config.getRedis().getChannel(), message);
            log.info("Published to channel={} message={}", config.getRedis().getChannel(), message);
        } catch (Exception e) {
            log.error("Redis Pub/Sub unavailable — event not published: {}", e.getMessage());
            // Non-fatal — the ban is already in Redis, Pub/Sub is just an optimisation
        }
    }
}