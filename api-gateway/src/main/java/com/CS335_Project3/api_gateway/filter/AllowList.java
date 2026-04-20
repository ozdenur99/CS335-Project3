package com.CS335_Project3.api_gateway.filter;

import com.CS335_Project3.api_gateway.config.AbuseDetectionConfig;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Maintains a list of trusted IPs that bypass all abuse detection checks.
 *
 * USE CASE:
 * Internal services, health check monitors, and known trusted clients
 * should never be blocked by abuse detection even if they send many
 * requests. The allowlist ensures they always get through.
 *
 * CONFIGURATION:
 * Add trusted IPs in application.properties:
 *   abuse.allowlist=127.0.0.1,10.0.0.1
 *
 * In-memory only — allowlist is for trusted internal IPs that are
 * known at startup. Unlike the blocklist, this doesn't need to be
 * shared across gateway instances via Redis.
 */
@Component
public class AllowList {

    private final Set<String> allowedIps;

    public AllowList(AbuseDetectionConfig config) {
        // Load from config at startup — immutable after that
        this.allowedIps = new HashSet<>(config.getAllowlist());
    }

    /**
     * Returns true if the given IP is on the allowlist
     * and should bypass all abuse checks.
     *
     * @param ip the remote IP address of the requester
     */
    public boolean isAllowed(String ip) {
        return allowedIps.contains(ip);
    }

    /**
     * Returns a snapshot of all allowed IPs.
     * Useful for the metrics/admin endpoint.
     */
    public Set<String> getAllowedIps() {
        return Set.copyOf(allowedIps);
    }
}