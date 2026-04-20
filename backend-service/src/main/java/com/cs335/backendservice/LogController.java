package com.cs335.backendservice;

import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

//receives log entries forwarded from the API gateway in real time
@RestController
@RequestMapping("/api/logs")
public class LogController {

    // stores received logs in memory (resets when restarted)
    // private final List<Map<String, Object>> receivedLogs = new ArrayList<>();
    // ArrayList is not thread-safe. Since both gateway1 and gateway2 POST logs
    // simultaneously,
    // we can use Collections.synchronizedList to wrap the ArrayList to make it
    // thread-safe.
    private final List<Map<String, Object>> receivedLogs = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Map<String, Object>> latestByGateway = new ConcurrentHashMap<>();

    // we also maintain a history of snapshots for the timeseries endpoint,
    // but we only keep the last 30 days of data to prevent memory issues
    private final List<Map<String, Object>> metricsHistory = new CopyOnWriteArrayList<>();

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
        metricsHistory.add(metrics);
        metricsHistory.removeIf(s -> {
            Object ts = s.get("timestamp");
            if (ts == null)
                return false;
            return Instant.ofEpochMilli(((Number) ts).longValue())
                    .isBefore(Instant.now().minus(30, ChronoUnit.DAYS));
        });
        return "ok";
    }

    @GetMapping("/metrics/latest")
    public Map<String, Map<String, Object>> getLatestMetrics() {
        // Dashboard calls this to get the latest snapshot from both gateways merged
        return latestByGateway;
    }

    @GetMapping("/metrics/timeseries")
    public List<Map<String, Object>> getTimeseries(
            @RequestParam(defaultValue = "week") String range,
            @RequestParam(required = false) String gatewayId,
            // Optional from and to parameters for custom date range filtering (timestamps in ms)
            // from and to are epoch milliseconds. When provided they take priority over range. 
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {

        // To allow custom date ranges, add from and to params to the existing endpoint
        long cutoff = (from != null) ? from
                : (range.equals("month")
                        ? Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli()
                        : Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli());

        long ceiling = (to != null) ? to : Instant.now().toEpochMilli();

        return metricsHistory.stream()
                .filter(s -> s.get("timestamp") != null
                        && ((Number) s.get("timestamp")).longValue() >= cutoff
                        && ((Number) s.get("timestamp")).longValue() <= ceiling)
                .filter(s -> gatewayId == null || gatewayId.equals(s.get("gatewayId")))
                .collect(Collectors.toList());
    }
}