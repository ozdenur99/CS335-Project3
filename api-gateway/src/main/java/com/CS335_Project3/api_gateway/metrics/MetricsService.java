package com.CS335_Project3.api_gateway.metrics;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

//@Component makes Spring create one single instance shared across the whole app
@Component
public class MetricsService {

    //we use an AtomicInteger (thread-safe counter)
    //as multiple requests can hit it at the same time
    private final AtomicInteger totalRequests   = new AtomicInteger(0);
    private final AtomicInteger blockedRequests = new AtomicInteger(0);

    //we set a ConcurrentHashMap which tracks request count per API key
    private final ConcurrentHashMap<String, AtomicInteger> perKeyCount
            = new ConcurrentHashMap<>();
    //is called by LoggingFilter after every request to record what happened
    public void recordRequest(String apiKey, boolean wasBlocked) {

        //increment total requests
        totalRequests.incrementAndGet();

        //if blocked, increment blocked requests counter
        if (wasBlocked) {
            blockedRequests.incrementAndGet();
        }

        //add key to map if new, then increment its count
        perKeyCount.computeIfAbsent(apiKey, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    //builds and returns a summary of all metrics as a Map
    //Spring converts this Map to JSON when returned by the MetricsController
    public Map<String, Object> getSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();

        int total   = totalRequests.get();
        int blocked = blockedRequests.get();

        snapshot.put("totalRequests",   total);
        snapshot.put("blockedRequests", blocked);
        snapshot.put("allowedRequests", total - blocked);
        snapshot.put("perKey",          perKeyCount);

        return snapshot;
    }
}