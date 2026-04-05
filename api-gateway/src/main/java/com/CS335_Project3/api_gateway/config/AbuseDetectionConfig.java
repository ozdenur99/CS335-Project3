package com.CS335_Project3.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configurable thresholds for abuse detection.
 *
 * Override defaults in application.properties:
 *   abuse.failure.maxFailuresPerWindow=5
 *   abuse.failure.windowSeconds=60
 *   abuse.blockDurationSeconds=300
 *   */
@Component
@ConfigurationProperties(prefix = "abuse")
public class AbuseDetectionConfig {


    private Failure failure = new Failure();

    /** How long an IP stays blocked before being automatically unblocked (seconds). */
    private int blockDurationSeconds = 300; // 5 minutes default

    public Failure getFailure() { return failure; }

    public int getBlockDurationSeconds() { return blockDurationSeconds; }
    public void setBlockDurationSeconds(int v) { this.blockDurationSeconds = v; }

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
}
