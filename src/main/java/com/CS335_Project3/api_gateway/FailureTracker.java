package com.CS335_Project3.api_gateway;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks abusive response patterns per client using a sliding window.
 *
 * WHAT IT TRACKS:
 * 429 (Too Many Requests) and 403 (Forbidden) responses.
 * Same sliding window algorithm as SpikeDetector.
 * Each client has a deque of failure timestamps. On every failure:
 *   1. Evict timestamps older than the window from the front.
 *   2. Add the current timestamp to the back.
 *   3. If count exceeds threshold → auto-block.
 *
 * THREAD SAFETY: same approach as SpikeDetector — ConcurrentHashMap + per-client deque synchronization.
 */
@Component
public class FailureTracker {

    private final Map<String, Deque<Instant>> failureLog = new ConcurrentHashMap<>();
    private final AbuseDetectionConfig config;

    public FailureTracker(AbuseDetectionConfig config) {
        this.config = config;
    }

    /**
     * Records a failure for {@code clientId} and returns true if the
     * failure threshold has been exceeded.
     *
     * Only called when response status is 429 or 403.
     * Called after filterChain.doFilter() so the actual response status is known.
     *
     * @param clientId API key or IP address of the requester
     * @return true if the failure threshold is exceeded
     */
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

    /**
     * Returns the current failure count within the active window.
     * Useful for metrics
     */
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

    public void reset() {
        failureLog.clear();
    }
}