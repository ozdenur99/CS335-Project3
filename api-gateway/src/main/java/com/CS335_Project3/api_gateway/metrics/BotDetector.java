package com.CS335_Project3.api_gateway.metrics;

import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//@Component makes Spring create one single instance shared across the whole app
@Component
public class BotDetector {

    //stores IPs flagged as suspicious
    private final Set<String> suspiciousIps = ConcurrentHashMap.newKeySet();

    //tracks request count per IP
    private final ConcurrentHashMap<String, AtomicInteger> ipRequestCount
            = new ConcurrentHashMap<>();

    //IP gets flagged  after exceeding the threshold set (50 requests)
    //private static final int SUSPICIOUS_THRESHOLD = 50;

    //risk levels for suspicious IPs
    private static final int LOW_RISK_THRESHOLD = 50;
    private static final int MEDIUM_RISK_THRESHOLD = 100;
    private static final int HIGH_RISK_THRESHOLD = 200;

    //called by LoggingFilter on every request
    public void record(String ip) {
        int count = ipRequestCount.computeIfAbsent(ip, k -> new AtomicInteger(0))
                .incrementAndGet();
        if (count > LOW_RISK_THRESHOLD) {
            suspiciousIps.add(ip);
        }
    }

    //returns risk level of IP based on how many requests it has made (low = 50, mid = 100, high = 200)
    public String getRiskLevel(String ip) {
        int count = ipRequestCount.getOrDefault(ip, new AtomicInteger(0)).get();
        if (count > HIGH_RISK_THRESHOLD)   return "HIGH";
        if (count > MEDIUM_RISK_THRESHOLD) return "MEDIUM";
        if (count > LOW_RISK_THRESHOLD)    return "LOW";
        return "NONE";
    }

    //returns all suspicious IPs grouped by risk level
    //called by MetricsController and shown via GET /metrics/suspicious/risk
    public Map<String, List<String>> getSuspiciousIpsByRisk() {
        Map<String, List<String>> grouped = new HashMap<>();
        grouped.put("HIGH",   new ArrayList<>());
        grouped.put("MEDIUM", new ArrayList<>());
        grouped.put("LOW",    new ArrayList<>());

        for (String ip : suspiciousIps) {
            String level = getRiskLevel(ip);
            if (grouped.containsKey(level)) {
                grouped.get(level).add(ip);
            }
        }
        return grouped;
    }

    //returns true if the given IP has been flagged as suspicious
    public boolean isSuspicious(String ip) {
        return suspiciousIps.contains(ip);
    }
    //returns an immutable snapshot of all currently flagged IPs
    //(exposed via GET /metrics/suspicious in MetricsController)
    public Set<String> getSuspiciousIps() {
        return Set.copyOf(suspiciousIps);
    }

    // clears all counts and flagged IPs to reset state between test cases
    public void reset() {
        ipRequestCount.clear();
        suspiciousIps.clear();
    }
}