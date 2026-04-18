package com.CS335_Project3.api_gateway;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects request spikes using a sliding window counter per client.
 * HOW THE SLIDING WINDOW WORKS:
 * Each client has a deque (double-ended queue) of timestamps.
 * On every incoming request:
 *   1. Remove timestamps from the front that are older than the window.
 *   2. Add the current timestamp to the back.
 *   3. If the remaining count exceeds the threshold --> spike detected.
 * THREAD SAFETY:
 * ConcurrentHashMap keeps the outer map safe for simultaneous access.
 * Each client's deque is synchronized individually so different clien}ts
 * never block each other, only concurrent requests from the same client queue up.
 */
@Component
public class Spike {

    private final Map<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();
    private final AbuseDetectionConfig config;

    public Spike(AbuseDetectionConfig config) {
        this.config = config;
    }

    /**
     * Records a request from {@code clientId} and returns true if the spike threshold has been exceeded.
     * @param clientId API key or IP address of the requester
     * @return true if a spike is detected
     */
    public boolean recordAndCheck(String clientId) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(config.getSpike().getWindowSeconds());

        Deque<Instant> timestamps = requestLog.computeIfAbsent(clientId, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            // Remove timestamps that have fallen outside the window
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }
            timestamps.addLast(now);
            return timestamps.size() > config.getSpike().getMaxRequestsPerWindow();
        }
    }

    /**
     * Returns the current request count within the active window.
     * Useful for metrics/logging
     */
    public int getRequestCount(String clientId) {
        Deque<Instant> timestamps = requestLog.get(clientId);
        if (timestamps == null) return 0;
        Instant windowStart = Instant.now().minusSeconds(config.getSpike().getWindowSeconds());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }
            return timestamps.size();
        }
    }

    public void reset() {
        requestLog.clear();
    }
}

