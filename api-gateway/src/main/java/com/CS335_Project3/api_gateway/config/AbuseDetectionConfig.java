package com.CS335_Project3.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.ArrayList;

/**
 * Configurable thresholds for abuse detection.
 *
 * Override defaults in application.properties:
 *   abuse.failure.maxFailuresPerWindow=5
 *   abuse.failure.windowSeconds=60
 *   abuse.blockDurationSeconds=300
 *   abuse.redis.channel=abuse-events
 *   abuse.allowlist=10.0.0.1,10.0.0.2
 *   abuse.risk.lowThreshold=2
 *   abuse.risk.mediumThreshold=4
 *   */
@Component
@ConfigurationProperties(prefix = "abuse")
public class AbuseDetectionConfig {


    private Failure failure = new Failure();
    private Redis redis = new Redis();
    private Risk risk = new Risk();

    /** How long an IP stays blocked before being automatically unblocked (seconds). */
    private int blockDurationSeconds = 300; // 5 minutes default

    /** Trusted IPs that bypass all abuse checks entirely. */
    private List<String> allowlist = new ArrayList<>();

    public Failure getFailure() { return failure; }
    public Redis getRedis() { return redis; }
    public Risk getRisk() { return risk; }
    public int getBlockDurationSeconds() { return blockDurationSeconds; }
    public void setBlockDurationSeconds(int v) { this.blockDurationSeconds = v; }
    public List<String> getAllowlist() { return allowlist; }
    public void setAllowlist(List<String> v) { this.allowlist = v; }

    public static class Failure {
        /** Max 403 failures allowed in the window before auto-blocking. */
        private int maxFailuresPerWindow = 5;
        /** Sliding window size in seconds. */
        private int windowSeconds = 60;

        public int getMaxFailuresPerWindow() { return maxFailuresPerWindow; }
        public void setMaxFailuresPerWindow(int v) { this.maxFailuresPerWindow = v; }
        public int getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(int v) { this.windowSeconds = v; }
    }
    public static class Redis {
        /** Redis channel for ban notifications between gateway instances. */
        private String channel = "abuse-events";

        public String getChannel() { return channel; }
        public void setChannel(String v) { this.channel = v; }
    }

    public static class Risk {
        /** Failure count threshold for LOW risk label. */
        private int lowThreshold = 2;
        /** Failure count threshold for MEDIUM risk label. */
        private int mediumThreshold = 4;

        public int getLowThreshold() { return lowThreshold; }
        public void setLowThreshold(int v) { this.lowThreshold = v; }
        public int getMediumThreshold() { return mediumThreshold; }
        public void setMediumThreshold(int v) { this.mediumThreshold = v; }
    }
}
