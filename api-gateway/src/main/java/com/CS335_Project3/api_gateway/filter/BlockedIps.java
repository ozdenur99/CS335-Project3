package com.CS335_Project3.api_gateway.filter;

import com.CS335_Project3.api_gateway.config.AbuseDetectionConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe IP blocklist with automatic cooldown-based unblocking.
 *
 * When an IP is blocked, the block time is recorded alongside it.
 * On every isBlocked() check, if the block duration has expired,
 * the IP is automatically removed from the blocklist.
 *
 * Block duration is configurable via application.properties:
 *   abuse.blockDurationSeconds=300  (default: 5 minutes)
 */

@Component
public class BlockedIps {

    // Maps IP address → time it was blocked
    private final Map<String, Instant> blockedIps = new ConcurrentHashMap<>();
    private final AbuseDetectionConfig config;

    public BlockedIps(AbuseDetectionConfig config) {
        this.config = config;
    }

    /**
     * Adds an IP to the blocklist with the current timestamp.
     *
     * @param ip e.g. "192.168.1.42"
     */
    public void block(String ip) {
        blockedIps.put(ip, Instant.now());
    }

    /**
     * Manually removes an IP from the blocklist.
     * Also called automatically when cooldown expires via isBlocked().
     */
    public void unblock(String ip) {
        blockedIps.remove(ip);
    }

    /**
     * Returns true if the given IP is currently blocked.
     * If the block duration has expired automatically unblocks the IP first.
     */
    public boolean isBlocked(String ip) {
        Instant blockedAt = blockedIps.get(ip);
        if (blockedAt == null) return false;

        // Check if the cooldown period has expired
        Instant expiry = blockedAt.plusSeconds(config.getBlockDurationSeconds());
        if (Instant.now().isAfter(expiry)) {
            // Block has expired, automatically unblock
            blockedIps.remove(ip);
            return false;
        }

        return true;
    }

    /**
     * Returns an immutable snapshot of all currently blocked IPs.
     * Expired blocks are filtered out automatically.
     * Useful for metrics.
     */
    public Set<String> getBlockedIps() {
        Instant now = Instant.now();
        // Filter out any expired blocks before returning
        return blockedIps.entrySet().stream()
                .filter(e -> now.isBefore(
                        e.getValue().plusSeconds(config.getBlockDurationSeconds())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Clears all blocked IPs. Used in tests to reset between test cases.
     */
    public void reset() {
        blockedIps.clear();
    }
}