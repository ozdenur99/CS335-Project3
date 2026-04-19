package com.cs335.backendservice;

import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;

//receives log entries forwarded from the API gateway in real time
@RestController
@RequestMapping("/api/logs")
public class LogController {

    // stores received logs in memory (resets when restarted)
    // private final List<Map<String, Object>> receivedLogs = new ArrayList<>();
    // ArrayList is not thread-safe. Since both gateway1 and gateway2 POST logs simultaneously, 
    // we can use Collections.synchronizedList to wrap the ArrayList to make it thread-safe.
    private final List<Map<String, Object>> receivedLogs = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Map<String, Object>> latestByGateway = new ConcurrentHashMap<>();

    // POST /api/logs (receives a log entry from the gateway)
    @PostMapping
    public String receiveLog(@RequestBody Map<String, Object> logEntry) {
        receivedLogs.add(logEntry);
        return "ok";
    }

    // GET /api/logs (returns all logs received from the gateway)
    @GetMapping
    public List<Map<String, Object>> getLogs() {
        return receivedLogs;
    }

    @PostMapping("/metrics")
    public String receiveMetrics(@RequestBody Map<String, Object> metrics) {
        String gatewayId = (String) metrics.getOrDefault("gatewayId", "unknown");
        latestByGateway.put(gatewayId, metrics);
        return "ok";
    }

    @GetMapping("/metrics/latest")
    public Map<String, Map<String, Object>> getLatestMetrics() {
        // Dashboard calls this to get the latest snapshot from both gateways merged
        return latestByGateway;
    }
}