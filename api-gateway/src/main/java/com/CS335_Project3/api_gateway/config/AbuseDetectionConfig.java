package com.CS335_Project3.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configurable thresholds for abuse detection.
 *
 * Override defaults in application.properties:
 *   abuse.spike.maxRequestsPerWindow=100
 *   abuse.spike.windowSeconds=10
 *   abuse.failure.maxFailuresPerWindow=5
 *   abuse.failure.windowSeconds=60
 */
@Component
@ConfigurationProperties(prefix = "abuse")
public class AbuseDetectionConfig {

    private Spike spike = new Spike();
    private Failure failure = new Failure();

    public Spike getSpike() { return spike; }
    public Failure getFailure() { return failure; }

    public static class Spike {
        private int maxRequestsPerWindow = 100;
        private int windowSeconds = 10;

        public int getMaxRequestsPerWindow() { return maxRequestsPerWindow; }
        public void setMaxRequestsPerWindow(int v) { this.maxRequestsPerWindow = v; }
        public int getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(int v) { this.windowSeconds = v; }
    }

    public static class Failure {
        private int maxFailuresPerWindow = 5;
        private int windowSeconds = 60;

        public int getMaxFailuresPerWindow() { return maxFailuresPerWindow; }
        public void setMaxFailuresPerWindow(int v) { this.maxFailuresPerWindow = v; }
        public int getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(int v) { this.windowSeconds = v; }
    }
}