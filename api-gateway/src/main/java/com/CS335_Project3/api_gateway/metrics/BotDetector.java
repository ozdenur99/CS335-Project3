package com.CS335_Project3.api_gateway.metrics;

import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

//@Component makes Spring create one single instance shared across the whole app
@Component
public class BotDetector {

    //stores IPs flagged as suspicious
    private final Set<String> suspiciousIps = ConcurrentHashMap.newKeySet();

    //tracks request count per IP
    private final ConcurrentHashMap<String, AtomicInteger> ipRequestCount
            = new ConcurrentHashMap<>();

    //IP gets flagged  after exceeding the threshold set (50 requests)
    private static final int SUSPICIOUS_THRESHOLD = 50;

    //called by LoggingFilter on every request
    public void record(String ip) {
        int count = ipRequestCount.computeIfAbsent(ip, k -> new AtomicInteger(0))
                .incrementAndGet();
        if (count > SUSPICIOUS_THRESHOLD) {
            suspiciousIps.add(ip);
        }
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