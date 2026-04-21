package com.CS335_Project3.api_gateway.metrics;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.CS335_Project3.api_gateway.RateLimiter;
import org.springframework.context.annotation.Lazy;

//@Component makes Spring create one single instance shared across the whole app
@Component
public class MetricsService {

    // we inject RateLimiter here so we can read client limits for risk score
    // calculation
    private final RateLimiter rateLimiter;

    public MetricsService(@Lazy RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    // AtomicInteger is a thread-safe counter, safe when multiple requests hit it at
    // the same time
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger blockedRequests = new AtomicInteger(0);

    // ConcurrentHashMap is a thread-safe map that tracks request count per API key
    private final ConcurrentHashMap<String, AtomicInteger> perKeyCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> perKeyBlockedCount = new ConcurrentHashMap<>();

    // stores latency values per API key so we can calculate percentiles later
    // key = apiKey, value = list of latency values in milliseconds
    private final ConcurrentHashMap<String, List<Long>> perKeyLatency = new ConcurrentHashMap<>();

    // tracks exact HTTP status code counts per API key
    // key = apiKey, value = map of status code to count
    // e.g. { "dev-key-token": { 200: 5, 429: 2 } }
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, AtomicInteger>> perKeyStatusCodes = new ConcurrentHashMap<>();

    // tracks how many requests each client has made in the current window
    // used to calculate risk score
    private final ConcurrentHashMap<String, AtomicInteger> perKeyRequestsInWindow = new ConcurrentHashMap<>();

    // gateway-level status code totals (across all keys)
    private final ConcurrentHashMap<Integer, AtomicInteger> statusCodeTotals = new ConcurrentHashMap<>();
    // the rate limit per client (used to calculate risk %)
    // matches the limits set in RateLimiter.java

    // This is the hardcoded value causing wrong risk scores for all non-dev keys,
    // instead use rateLimiter.getClientLimit()
    // private static final int DEFAULT_LIMIT = 3;

    // called by LoggingFilter after every request to record what happened
    public void recordRequest(String apiKey, boolean wasBlocked) {
        // increment total and blocked counters
        totalRequests.incrementAndGet();
        if (wasBlocked) {
            blockedRequests.incrementAndGet();
            perKeyBlockedCount.computeIfAbsent(apiKey, k -> new AtomicInteger(0))
                    .incrementAndGet();
        }
        // add key to map if new then increment its count
        perKeyCount.computeIfAbsent(apiKey, k -> new AtomicInteger(0))
                .incrementAndGet();

        // increment requests in window for risk score tracking
        perKeyRequestsInWindow.computeIfAbsent(apiKey, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    // overloaded method that also accepts latency and status code
    // called by LoggingFilter when we have the full request details
    public void recordRequest(String apiKey, boolean wasBlocked, long latencyMs, int statusCode, String gatewayId) {
        // call the base method first for the existing counters
        recordRequest(apiKey, wasBlocked);

        // store latency value for this client
        // computeIfAbsent creates a new list if this key hasn't been seen before
        // synchronized so two requests for the same key don't corrupt the list
        perKeyLatency.computeIfAbsent(apiKey, k -> Collections.synchronizedList(new ArrayList<>())).add(latencyMs);

        // store status code count for this client
        perKeyStatusCodes
                .computeIfAbsent(apiKey, k -> new ConcurrentHashMap<>())
                // add per-key status code counting
                .computeIfAbsent(statusCode, k -> new AtomicInteger(0))
                .incrementAndGet();
        // also update gateway-level status code totals
        if (statusCode > 0) {
            statusCodeTotals.computeIfAbsent(statusCode, k -> new AtomicInteger(0)).incrementAndGet();
        }
    }

    // calculates latency percentiles for a given client
    // p50 = median, p95 = 95th percentile, p99 = 99th percentile
    public Map<String, Long> getLatencyPercentiles(String apiKey) {
        List<Long> values = perKeyLatency.get(apiKey);
        Map<String, Long> percentiles = new HashMap<>();

        if (values == null || values.isEmpty()) {
            percentiles.put("p50", 0L);
            percentiles.put("p95", 0L);
            percentiles.put("p99", 0L);
            return percentiles;
        }

        // copy and sort the list so we can pick values at specific positions
        List<Long> sorted;
        synchronized (values) {
            sorted = new ArrayList<>(values);
        }
        Collections.sort(sorted);

        percentiles.put("p50", sorted.get((int) (sorted.size() * 0.50)));
        percentiles.put("p95", sorted.get((int) (sorted.size() * 0.95)));
        percentiles.put("p99", sorted.get((int) (sorted.size() * 0.99)));
        return percentiles;
    }

    // returns status code breakdown for a given client
    public Map<Integer, Integer> getStatusCodes(String apiKey) {
        ConcurrentHashMap<Integer, AtomicInteger> codes = perKeyStatusCodes.get(apiKey);
        if (codes == null)
            return new HashMap<>();
        Map<Integer, Integer> result = new HashMap<>();
        codes.forEach((code, count) -> result.put(code, count.get()));
        return result;
    }

    // calculates how close a client is to hitting the rate limit as a percentage
    // 0% = no requests made, 100% = at the limit or over
    public int getRiskScore(String apiKey) {
        int count = perKeyRequestsInWindow.getOrDefault(apiKey, new AtomicInteger(0)).get();
        // int score = (int) ((count / (double) DEFAULT_LIMIT) * 100);

        // Now each client uses its own actual limit.
        // so the percentage is meaningful for every key.
        int limit = rateLimiter.getClientLimit(apiKey);
        int score = (int) ((count / (double) limit) * 100);

        return Math.min(score, 100); // cap at 100%
    }

    // builds and returns a summary of all current metrics as a Map
    // Spring converts this Map to JSON automatically when returned by
    // MetricsController
    public Map<String, Object> getSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();

        int total = totalRequests.get();
        int blocked = blockedRequests.get();

        snapshot.put("totalRequests", total);
        snapshot.put("blockedRequests", blocked);
        snapshot.put("allowedRequests", total - blocked);
        snapshot.put("perKey", perKeyCount);

        // add risk scores for all tracked clients
        Map<String, Integer> riskScores = new HashMap<>();
        perKeyCount.forEach((key, count) -> riskScores.put(key, getRiskScore(key)));
        snapshot.put("riskScores", riskScores);
        snapshot.put("statusCodes", getStatusCodeTotals());

        return snapshot;
    }

    // returns all tracked API keys (used by MetricsExporter)
    public ConcurrentHashMap<String, AtomicInteger> getPerKeyCount() {
        return perKeyCount;
    }

    // resets the per-window request counts (call this when windows reset)
    public void resetWindowCounts() {
        perKeyRequestsInWindow.clear();
    }

    public ConcurrentHashMap<String, AtomicInteger> getPerKeyBlockedCount() {
        return perKeyBlockedCount;
    }

    // returns the gateway-level status code totals (used by MetricsExporter)
    public Map<Integer, Integer> getStatusCodeTotals() {
        Map<Integer, Integer> result = new HashMap<>();
        statusCodeTotals.forEach((code, count) -> result.put(code, count.get()));
        return result;
    }
}