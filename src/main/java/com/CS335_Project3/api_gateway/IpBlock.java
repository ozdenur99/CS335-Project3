package com.CS335_Project3.api_gateway;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains a blocklist of IP addresses.
 * IPs will be added automatically when either
 * Spike or FailureTracker trips their threshold.
 */
@Component
public class IpBlock {

    private final Set<String> blockedIps = ConcurrentHashMap.newKeySet();

    /**
     * Adds an IP to the blocklist.
     * Called automatically on abuse detection, or manually by an admin.
     * @param ip IP thats getting blocked
     */
    public void block(String ip) {
        blockedIps.add(ip);
    }

    /**
     * Removes an IP from the blocklist.
     * @param ip the IP to unblock
     */
    public void unblock(String ip) {
        blockedIps.remove(ip);
    }

    /**
     * Returns true if the given IP is currently blocked.
     * Called on every incoming request
     */
    public boolean isBlocked(String ip) {
        return blockedIps.contains(ip);
    }

    /**
     * Returns an immutable snapshot of all currently blocked IPs.
     * Useful for logging and metrics
     */
    public Set<String> getBlockedIps() {
        return Set.copyOf(blockedIps);
    }

    public void reset() {
        blockedIps.clear();
    }
}