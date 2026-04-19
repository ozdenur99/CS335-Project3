package com.CS335_Project3.api_gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Subscribes to the Redis abuse-events channel and reacts to ban/unban
 * messages published by other gateway instances.
 *
 * WHY THIS EXISTS:
 * When gateway-2 receives a BLOCK message from gateway-1, it doesn't
 * need to do anything to BlockedIps — the ban is already in Redis and
 * isBlocked() reads from Redis directly. The subscriber's job is just
 * to LOG the event so the local gateway's metrics and logs reflect
 * what happened across the distributed system.
 *
 * If we later add a local in-memory cache (for performance), this
 * subscriber would also update that cache. For now, logging is enough.
 */
@Component
public class AbuseEventSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(AbuseEventSubscriber.class);

    private final BlockedIps blockedIps;

    public AbuseEventSubscriber(BlockedIps blockedIps) {
        this.blockedIps = blockedIps;
    }

    /**
     * Called by Spring Data Redis when a message arrives on the abuse-events channel.
     *
     * @param message the raw Redis message
     * @param pattern the channel pattern (not used here)
     */
    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        String body = new String(message.getBody());
        log.info("Received abuse event: {}", body);

        if (body.startsWith("BLOCK:")) {
            String clientId = body.substring("BLOCK:".length());
            // The ban is already in Redis — we just log it locally
            // so this gateway's logs show the distributed ban event
            log.warn("[Distributed ban] clientId={} blocked by another gateway instance", clientId);

        } else if (body.startsWith("UNBLOCK:")) {
            String clientId = body.substring("UNBLOCK:".length());
            log.info("[Distributed unban] clientId={} unblocked by another gateway instance", clientId);
        }
    }
}