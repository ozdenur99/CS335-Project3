package com.cs335.backendservice;

import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.time.Instant;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.Objects;
import java.util.Set;
import java.time.temporal.ChronoUnit;

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

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String HISTORY_KEY = "metrics:history";

    // Constructor injection of Redis template
    public LogController(StringRedisTemplate redis) {
        this.redis = redis;
    }

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
        // Store in Redis sorted set with timestamp as score for time-based retrieval
        // If timestamp is missing, use current time as fallback
        // opsForZSet().add(key, value, score) stores the snapshot as JSON with the
        // timestamp as the sort score.
        try {
            String json = objectMapper.writeValueAsString(metrics);
            long ts = ((Number) metrics.getOrDefault("timestamp", 0)).longValue();
            redis.opsForZSet().add(HISTORY_KEY, json, ts);
            // removeRangeByScore prunes anything older than 30 days to prevent unbounded growth. 
            // redis.opsForZSet().removeRangeByScore(HISTORY_KEY, 0,
            //         Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli());
        } catch (Exception e) {
            // fail silently
        }
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
            // Optional from and to parameters for custom date range filtering (timestamps
            // in ms)
            // from and to are epoch milliseconds. When provided they take priority over
            // range.
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {

        // To allow custom date ranges, add from and to params to the existing endpoint
        long cutoff = (from != null) ? from
                : (range.equals("month")
                        ? Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli()
                        : Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli());

        long ceiling = (to != null) ? to : Instant.now().toEpochMilli();
        // rangeByScore(key, min, max) queries Redis for all entries whose score (timestamp) 
        // falls between cutoff and ceiling, this is the time range filter.        
        Set<String> raw = redis.opsForZSet().rangeByScore(HISTORY_KEY, cutoff, ceiling);
        if (raw == null)
            return List.of();
        return raw.stream()
                .map(s -> {
                    try {
                        // each entry comes back as a JSON string, so we deserialize it back to a Map. 
                        // if parsing fails, we return null and filter it out later.
                        return objectMapper.readValue(s, Map.class);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                // filter by gatewayId if provided.   
                .filter(s -> gatewayId == null || gatewayId.equals(s.get("gatewayId")))
                .collect(Collectors.toList());
    }
}