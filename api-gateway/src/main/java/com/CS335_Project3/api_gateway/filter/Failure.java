package com.CS335_Project3.api_gateway.filter;

import com.CS335_Project3.api_gateway.config.AbuseDetectionConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks 429 and 403 failure responses per client using a sliding window.
 * Same sliding window algorithm as SpikeDetector.
 */
@Component
public class Failure {

    private final Map<String, Deque<Instant>> failureLog = new ConcurrentHashMap<>();
    private final AbuseDetectionConfig config;

    public Failure(AbuseDetectionConfig config) {
        this.config = config;
    }

    public boolean recordAndCheck(String clientId) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(config.getFailure().getWindowSeconds());
        Deque<Instant> timestamps = failureLog.computeIfAbsent(clientId, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }
            timestamps.addLast(now);
            return timestamps.size() > config.getFailure().getMaxFailuresPerWindow();
        }
    }

    public int getFailureCount(String clientId) {
        Deque<Instant> timestamps = failureLog.get(clientId);
        if (timestamps == null) return 0;
        Instant windowStart = Instant.now().minusSeconds(config.getFailure().getWindowSeconds());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }
            return timestamps.size();
        }
    }

    public void reset() { failureLog.clear(); }
}