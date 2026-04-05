package com.CS335_Project3.api_gateway.filter;

import com.CS335_Project3.api_gateway.config.AbuseDetectionConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects request spikes using a sliding window counter per client.
 *
 * Each client has a deque of timestamps. On every request:
 *   1. Remove timestamps older than the window from the front.
 *   2. Add current timestamp to the back.
 *   3. If count exceeds threshold → spike detected.
 *
 * Thread-safe: ConcurrentHashMap for the outer map,
 * synchronized per-client deque so different clients never block each other.
 */
@Component
public class Spike {

    private final Map<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();
    private final AbuseDetectionConfig config;

    public Spike(AbuseDetectionConfig config) {
        this.config = config;
    }

    public boolean recordAndCheck(String clientId) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(config.getSpike().getWindowSeconds());
        Deque<Instant> timestamps = requestLog.computeIfAbsent(clientId, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }
            timestamps.addLast(now);
            return timestamps.size() > config.getSpike().getMaxRequestsPerWindow();
        }
    }

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

    public void reset() { requestLog.clear(); }
}