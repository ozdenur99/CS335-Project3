package com.CS335_Project3.api_gateway.metrics;

import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.Collections;
import java.util.stream.Collectors;

//@Component makes Spring create one single instance shared across the whole app

// BotDetector identifies suspicious activity by tracking request counts per IP.
// It uses Redis to share data across multiple Gateway instances.
@Component
public class BotDetector {

    // //stores IPs flagged as suspicious
    // private final Set<String> suspiciousIps = ConcurrentHashMap.newKeySet();

    // //tracks request count per IP
    // private final ConcurrentHashMap<String, AtomicInteger> ipRequestCount
    // = new ConcurrentHashMap<>();

    // IP gets flagged after exceeding the threshold set (50 requests)
    // private static final int SUSPICIOUS_THRESHOLD = 50;

    // risk levels for suspicious IPs
    private static final int LOW_RISK_THRESHOLD = 50;
    private static final int MEDIUM_RISK_THRESHOLD = 100;
    private static final int HIGH_RISK_THRESHOLD = 200;

    // Redis Key Constants
    private static final String COUNT_PREFIX = "bot:count:";
    private static final String IP_SET_KEY = "bot:all_ips";

    private final StringRedisTemplate redis;

    public BotDetector(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // called by LoggingFilter on every request
    // public void record(String ip) {
    // int count = ipRequestCount.computeIfAbsent(ip, k -> new AtomicInteger(0))
    // .incrementAndGet();
    // if (count > LOW_RISK_THRESHOLD) {
    // suspiciousIps.add(ip);
    // }
    // }

    /**
     * Increments the request count for a specific IP in Redis.
     * Also tracks the IP in a centralized Set for easy retrieval.
     */
    public void record(String ip) {
        String countKey = COUNT_PREFIX + ip;
        // Increment the individual counter
        redis.opsForValue().increment(countKey);
        // Add IP to the global set of known IPs (for reporting)
        redis.opsForSet().add(IP_SET_KEY, ip);
    }

    /**
     * Checks if an IP has exceeded the minimum suspicious threshold.
     */
    public boolean isSuspicious(String ip) {
        return getCount(ip) > LOW_RISK_THRESHOLD;
    }

    // returns risk level of IP based on how many requests it has made (low = 50,
    // mid = 100, high = 200)
    public String getRiskLevel(String ip) {
        // int count = ipRequestCount.getOrDefault(ip, new AtomicInteger(0)).get();
        int count = getCount(ip);
        if (count > HIGH_RISK_THRESHOLD)
            return "HIGH";
        if (count > MEDIUM_RISK_THRESHOLD)
            return "MEDIUM";
        if (count > LOW_RISK_THRESHOLD)
            return "LOW";
        return "NONE";
    }

    // returns all suspicious IPs grouped by risk level
    // called by MetricsController and shown via GET /metrics/suspicious/risk
    public Map<String, List<String>> getSuspiciousIpsByRisk() {
        Map<String, List<String>> grouped = new HashMap<>();
        grouped.put("HIGH", new ArrayList<>());
        grouped.put("MEDIUM", new ArrayList<>());
        grouped.put("LOW", new ArrayList<>());

        Set<String> allIps = redis.opsForSet().members(IP_SET_KEY);
        if (allIps == null) {
            return grouped; // No IPs recorded yet
        }

        // for (String ip : suspiciousIps) {
        for (String ip : allIps) {
            String level = getRiskLevel(ip);
            if (grouped.containsKey(level)) {
                grouped.get(level).add(ip);
            }
        }
        return grouped;
    }

    // returns true if the given IP has been flagged as suspicious
    // public boolean isSuspicious(String ip) {
    // return suspiciousIps.contains(ip);
    // }

    // returns an immutable snapshot of all currently flagged IPs
    // (exposed via GET /metrics/suspicious in MetricsController)

    public Set<String> getSuspiciousIps() {
        // comment this return Set.copyOf(suspiciousIps);
        Set<String> allIps = redis.opsForSet().members(IP_SET_KEY);
        if (allIps == null)
            return Collections.emptySet();
        // *Returns a set of all IPs currently flagged as suspicious.
        // It filters the global set of known IPs to include only those
        // that exceed the suspicious threshold.
        return allIps.stream()
                .filter(this::isSuspicious)
                .collect(Collectors.toSet());
    }

    // method to fetch and parse the count from Redis.
    private int getCount(String ip) {
        String val = redis.opsForValue().get(COUNT_PREFIX + ip);
        return val == null ? 0 : Integer.parseInt(val);
    }

    // clears all bot-related data from redis to reset state between test cases
    public void reset() {
        // ipRequestCount.clear();
        // suspiciousIps.clear();
        Set<String> allIps = redis.opsForSet().members(IP_SET_KEY);
        if (allIps != null && !allIps.isEmpty()) {
            // create a list of keys to delete for all IP counts
            List<String> keysToDelete = new ArrayList<>();
            for (String ip : allIps) {
                keysToDelete.add(COUNT_PREFIX + ip);
            }
            redis.delete(keysToDelete);
        }
        // Delete the master IP set
        redis.delete(IP_SET_KEY);
    }
}