package com.CS335_Project3.api_gateway.metrics;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

//@Component makes Spring create one single instance shared across the whole app
@Component
public class MetricsService {

    private static final String EVENTS_ZSET = "metrics:events";
    private static final String TOTAL_KEY = "metrics:total";
    private static final String BLOCKED_KEY = "metrics:blocked";
    private static final String PER_KEY_TOTAL_HASH = "metrics:perKey:total";
    private static final String REQ_MINUTE_HASH = "metrics:req:minute";
    private static final String REQ_HOUR_HASH = "metrics:req:hour";
    private static final String LATENCY_SUM_MINUTE_HASH = "metrics:latency:sum:minute";
    private static final String LATENCY_COUNT_MINUTE_HASH = "metrics:latency:count:minute";
    private static final String CLIENTS_SET = "metrics:clients";
    private static final String BLOCKED_BY_ALGORITHM_HASH = "metrics:blocked:algorithm";
    private static final String BLOCKED_BY_REASON_HASH = "metrics:blocked:reason";
    private static final long RAW_EVENT_RETENTION_MS = 24L * 60L * 60L * 1000L;
    private static final List<Integer> DASHBOARD_STATUS_CODES = List.of(200, 400, 401, 403, 429, 500);

    private final StringRedisTemplate redisTemplate;

    public MetricsService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //kept for compatibility with older code paths
    public void recordRequest(String apiKey, boolean wasBlocked) {
        recordRequest(
                apiKey,
                "UNKNOWN",
                "UNKNOWN",
                wasBlocked ? 403 : 200,
                0L,
                "unknown",
                wasBlocked ? "BLOCKED" : "ALLOWED",
                wasBlocked ? "blocked" : "ok"
        );
    }

    //is called by LoggingFilter after every request to record full details
    public void recordRequest(String apiKey,
                              String ip,
                              String path,
                              int statusCode,
                              long latencyMs,
                              String algorithm,
                              String decision,
                              String reason) {
        long now = System.currentTimeMillis();
        long minuteBucket = toMinuteBucket(now);
        long hourBucket = toHourBucket(now);
        String normalizedApiKey = normalize(apiKey);
        String normalizedIp = normalize(ip);
        String normalizedPath = normalize(path);
        String normalizedAlgorithm = normalize(algorithm);
        String normalizedDecision = normalize(decision);
        String normalizedReason = normalize(reason);
        String clientId = toClientId(normalizedApiKey, normalizedIp);

        redisTemplate.opsForValue().increment(TOTAL_KEY, 1);
        if ("BLOCKED".equalsIgnoreCase(normalizedDecision)) {
            redisTemplate.opsForValue().increment(BLOCKED_KEY, 1);
            redisTemplate.opsForHash().increment(BLOCKED_BY_ALGORITHM_HASH, normalizedAlgorithm, 1);
            redisTemplate.opsForHash().increment(BLOCKED_BY_REASON_HASH, normalizedReason, 1);
        }

        redisTemplate.opsForHash().increment(PER_KEY_TOTAL_HASH, normalizedApiKey, 1);
        redisTemplate.opsForHash().increment(REQ_MINUTE_HASH, String.valueOf(minuteBucket), 1);
        redisTemplate.opsForHash().increment(REQ_HOUR_HASH, String.valueOf(hourBucket), 1);
        redisTemplate.opsForHash().increment(statusMinuteKey(statusCode), String.valueOf(minuteBucket), 1);
        redisTemplate.opsForHash().increment(LATENCY_SUM_MINUTE_HASH, String.valueOf(minuteBucket), latencyMs);
        redisTemplate.opsForHash().increment(LATENCY_COUNT_MINUTE_HASH, String.valueOf(minuteBucket), 1);
        redisTemplate.opsForHash().increment(clientMinuteKey(clientId), String.valueOf(minuteBucket), 1);
        redisTemplate.opsForSet().add(CLIENTS_SET, clientId);

        String event = serializeEvent(now, normalizedApiKey, normalizedIp, normalizedPath, statusCode,
                latencyMs, normalizedAlgorithm, normalizedDecision, normalizedReason);
        redisTemplate.opsForZSet().add(EVENTS_ZSET, event, now);
        redisTemplate.opsForZSet().removeRangeByScore(EVENTS_ZSET, 0, now - RAW_EVENT_RETENTION_MS);
    }

    //builds and returns a summary of all metrics as a Map
    //Spring converts this Map to JSON when returned by the MetricsController
    public Map<String, Object> getSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();

        long total = parseLong(redisTemplate.opsForValue().get(TOTAL_KEY));
        long blocked = parseLong(redisTemplate.opsForValue().get(BLOCKED_KEY));
        Map<Object, Object> perKeyRaw = redisTemplate.opsForHash().entries(PER_KEY_TOTAL_HASH);
        Map<String, Long> perKey = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : perKeyRaw.entrySet()) {
            perKey.put(String.valueOf(entry.getKey()), parseLong(String.valueOf(entry.getValue())));
        }

        snapshot.put("totalRequests", total);
        snapshot.put("blockedRequests", blocked);
        snapshot.put("allowedRequests", Math.max(total - blocked, 0));
        snapshot.put("perKey", perKey);
        snapshot.put("retentionHours", 24);
        return snapshot;
    }

    public Map<String, Object> getOverallTrend(int minutes) {
        int safeMinutes = clamp(minutes, 5, 1440);
        long now = System.currentTimeMillis();
        long startBucket = toMinuteBucket(now - (long) safeMinutes * 60_000L);
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(REQ_MINUTE_HASH);
        return trendResponse(safeMinutes, startBucket, raw);
    }

    public Map<String, Object> getClientTrend(String apiKey, String ip, int minutes) {
        int safeMinutes = clamp(minutes, 5, 1440);
        long now = System.currentTimeMillis();
        long startBucket = toMinuteBucket(now - (long) safeMinutes * 60_000L);
        String clientId = toClientId(normalize(apiKey), normalize(ip));
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(clientMinuteKey(clientId));
        Map<String, Object> response = trendResponse(safeMinutes, startBucket, raw);
        response.put("client", clientId);
        return response;
    }

    public Map<String, Object> getStatusTrend(int minutes) {
        int safeMinutes = clamp(minutes, 5, 1440);
        long now = System.currentTimeMillis();
        long startBucket = toMinuteBucket(now - (long) safeMinutes * 60_000L);
        long endBucket = toMinuteBucket(now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("windowMinutes", safeMinutes);

        List<Map<String, Object>> points = new ArrayList<>();
        for (long bucket = startBucket; bucket <= endBucket; bucket += 60_000L) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("timestamp", Instant.ofEpochMilli(bucket).toString());

            Map<String, Long> counts = new LinkedHashMap<>();
            for (Integer status : DASHBOARD_STATUS_CODES) {
                Object rawCount = redisTemplate.opsForHash().get(statusMinuteKey(status), String.valueOf(bucket));
                counts.put(String.valueOf(status), parseLong(rawCount == null ? "0" : rawCount.toString()));
            }
            point.put("counts", counts);
            points.add(point);
        }
        response.put("points", points);
        return response;
    }

    public Map<String, Object> getStatusDistribution(int minutes) {
        int safeMinutes = clamp(minutes, 5, 1440);
        long now = System.currentTimeMillis();
        long startTime = now - (long) safeMinutes * 60_000L;
        List<MetricEvent> events = getEvents(startTime, now, null, null);

        Map<String, Long> distribution = new LinkedHashMap<>();
        for (Integer code : DASHBOARD_STATUS_CODES) {
            distribution.put(String.valueOf(code), 0L);
        }
        for (MetricEvent event : events) {
            distribution.compute(String.valueOf(event.statusCode), (k, v) -> (v == null ? 0L : v) + 1L);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("windowMinutes", safeMinutes);
        response.put("total", events.size());
        response.put("distribution", distribution);
        return response;
    }

    public Map<String, Object> getLatencyTrend(int minutes) {
        int safeMinutes = clamp(minutes, 5, 1440);
        long now = System.currentTimeMillis();
        long startTime = now - (long) safeMinutes * 60_000L;
        List<MetricEvent> events = getEvents(startTime, now, null, null);

        Map<Long, List<Long>> byBucket = new HashMap<>();
        for (MetricEvent event : events) {
            long bucket = toMinuteBucket(event.timestampMs);
            byBucket.computeIfAbsent(bucket, k -> new ArrayList<>()).add(event.latencyMs);
        }

        List<Map<String, Object>> points = new ArrayList<>();
        long startBucket = toMinuteBucket(startTime);
        long endBucket = toMinuteBucket(now);
        for (long bucket = startBucket; bucket <= endBucket; bucket += 60_000L) {
            List<Long> latencies = byBucket.getOrDefault(bucket, List.of());
            List<Long> sorted = new ArrayList<>(latencies);
            sorted.sort(Comparator.naturalOrder());

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("timestamp", Instant.ofEpochMilli(bucket).toString());
            point.put("count", sorted.size());
            point.put("avgMs", sorted.isEmpty() ? 0.0 : round2(sorted.stream().mapToLong(Long::longValue).average().orElse(0)));
            point.put("p50Ms", sorted.isEmpty() ? 0 : percentile(sorted, 0.50));
            point.put("p95Ms", sorted.isEmpty() ? 0 : percentile(sorted, 0.95));
            points.add(point);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("windowMinutes", safeMinutes);
        response.put("points", points);
        return response;
    }

    public Map<String, Object> getAlgorithmBlockingComparison(int minutes) {
        int safeMinutes = clamp(minutes, 5, 1440);
        long now = System.currentTimeMillis();
        long startTime = now - (long) safeMinutes * 60_000L;
        List<MetricEvent> events = getEvents(startTime, now, null, null);

        Map<String, Long> byAlgorithm = new HashMap<>();
        Map<String, Long> byReason = new HashMap<>();
        for (MetricEvent event : events) {
            if (!"BLOCKED".equalsIgnoreCase(event.decision)) {
                continue;
            }
            byAlgorithm.compute(event.algorithm, (k, v) -> (v == null ? 0L : v) + 1L);
            byReason.compute(event.reason, (k, v) -> (v == null ? 0L : v) + 1L);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("windowMinutes", safeMinutes);
        response.put("blockedByAlgorithm", sortDesc(byAlgorithm));
        response.put("blockedByReason", sortDesc(byReason));
        return response;
    }

    public Map<String, Object> getRiskLeaderboard(int minutes, int limit) {
        int safeMinutes = clamp(minutes, 5, 1440);
        int safeLimit = clamp(limit, 1, 100);
        long now = System.currentTimeMillis();
        long startTime = now - (long) safeMinutes * 60_000L;
        List<MetricEvent> events = getEvents(startTime, now, null, null);

        Map<String, ClientRiskStats> perClient = new HashMap<>();
        for (MetricEvent event : events) {
            String client = toClientId(event.apiKey, event.ip);
            ClientRiskStats stats = perClient.computeIfAbsent(client, k -> new ClientRiskStats(event.apiKey, event.ip));
            stats.total++;
            if (event.statusCode == 403) stats.forbidden403++;
            if (event.statusCode == 429) stats.rateLimited429++;
            if (event.statusCode >= 500) stats.serverErrors5xx++;
            if ("BLOCKED".equalsIgnoreCase(event.decision)) stats.blocked++;
        }

        List<Map<String, Object>> leaderboard = perClient.values().stream()
                .map(stats -> buildRiskRow(stats, safeMinutes))
                .sorted((a, b) -> Double.compare((Double) b.get("riskScore"), (Double) a.get("riskScore")))
                .limit(safeLimit)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("windowMinutes", safeMinutes);
        response.put("items", leaderboard);
        return response;
    }

    public Map<String, Object> getClientDetail(String apiKey, String ip, int minutes, int limit) {
        int safeMinutes = clamp(minutes, 5, 1440);
        int safeLimit = clamp(limit, 1, 200);
        long now = System.currentTimeMillis();
        long startTime = now - (long) safeMinutes * 60_000L;
        String normalizedApiKey = normalize(apiKey);
        String normalizedIp = normalize(ip);
        List<MetricEvent> events = getEvents(startTime, now, normalizedApiKey, normalizedIp);

        Map<String, Long> status = new HashMap<>();
        long blocked = 0;
        long latencySum = 0;
        for (MetricEvent event : events) {
            status.compute(String.valueOf(event.statusCode), (k, v) -> (v == null ? 0L : v) + 1L);
            if ("BLOCKED".equalsIgnoreCase(event.decision)) blocked++;
            latencySum += event.latencyMs;
        }

        List<Map<String, Object>> recent = events.stream()
                .sorted((a, b) -> Long.compare(b.timestampMs, a.timestampMs))
                .limit(safeLimit)
                .map(this::toEventMap)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("windowMinutes", safeMinutes);
        response.put("client", toClientId(normalizedApiKey, normalizedIp));
        response.put("requestCount", events.size());
        response.put("blockedCount", blocked);
        response.put("avgLatencyMs", events.isEmpty() ? 0.0 : round2((double) latencySum / events.size()));
        response.put("statusDistribution", sortDesc(status));
        response.put("recentEvents", recent);
        return response;
    }

    private Map<String, Object> buildRiskRow(ClientRiskStats stats, int windowMinutes) {
        double velocityRatio = Math.min(1.0, stats.total / 50.0);
        double blockRatio = stats.total == 0 ? 0.0 : (double) stats.blocked / stats.total;
        double failureRatio = stats.total == 0 ? 0.0 : (double) (stats.forbidden403 + stats.rateLimited429 + stats.serverErrors5xx) / stats.total;
        double risk = (velocityRatio * 40.0) + (failureRatio * 35.0) + (blockRatio * 25.0);
        risk = Math.min(100.0, risk);

        List<String> reasons = new ArrayList<>();
        if (velocityRatio > 0.7) reasons.add("High request velocity");
        if (stats.rateLimited429 > 0) reasons.add("Rate-limit pressure (429)");
        if (stats.forbidden403 > 0) reasons.add("Forbidden responses detected (403)");
        if (stats.serverErrors5xx > 0) reasons.add("Backend/server errors (5xx) present");
        if (reasons.isEmpty()) reasons.add("Normal behavior");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("client", toClientId(stats.apiKey, stats.ip));
        row.put("apiKey", stats.apiKey);
        row.put("ip", stats.ip);
        row.put("windowMinutes", windowMinutes);
        row.put("requestCount", stats.total);
        row.put("blockedCount", stats.blocked);
        row.put("status403", stats.forbidden403);
        row.put("status429", stats.rateLimited429);
        row.put("status5xx", stats.serverErrors5xx);
        row.put("riskScore", round2(risk));
        row.put("riskReason", reasons);
        return row;
    }

    private Map<String, Object> trendResponse(int minutes, long startBucket, Map<Object, Object> raw) {
        long now = System.currentTimeMillis();
        long endBucket = toMinuteBucket(now);

        Map<String, Long> normalized = new HashMap<>();
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            normalized.put(String.valueOf(entry.getKey()), parseLong(String.valueOf(entry.getValue())));
        }

        List<Map<String, Object>> points = new ArrayList<>();
        for (long bucket = startBucket; bucket <= endBucket; bucket += 60_000L) {
            long count = normalized.getOrDefault(String.valueOf(bucket), 0L);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("timestamp", Instant.ofEpochMilli(bucket).toString());
            point.put("count", count);
            points.add(point);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("windowMinutes", minutes);
        response.put("points", points);
        return response;
    }

    private List<MetricEvent> getEvents(long start, long end, String apiKeyFilter, String ipFilter) {
        Set<String> rawEvents = redisTemplate.opsForZSet().rangeByScore(EVENTS_ZSET, start, end);
        if (rawEvents == null || rawEvents.isEmpty()) {
            return List.of();
        }

        List<MetricEvent> parsed = new ArrayList<>();
        for (String raw : rawEvents) {
            MetricEvent event = parseEvent(raw);
            if (event == null) {
                continue;
            }
            if (apiKeyFilter != null && !Objects.equals(event.apiKey, apiKeyFilter)) {
                continue;
            }
            if (ipFilter != null && !Objects.equals(event.ip, ipFilter)) {
                continue;
            }
            parsed.add(event);
        }
        return parsed;
    }

    private Map<String, Object> toEventMap(MetricEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", Instant.ofEpochMilli(event.timestampMs).toString());
        map.put("apiKey", event.apiKey);
        map.put("ip", event.ip);
        map.put("path", event.path);
        map.put("statusCode", event.statusCode);
        map.put("latencyMs", event.latencyMs);
        map.put("algorithm", event.algorithm);
        map.put("decision", event.decision);
        map.put("reason", event.reason);
        return map;
    }

    private static String clientMinuteKey(String clientId) {
        return "metrics:client:minute:" + clientId;
    }

    private static String statusMinuteKey(int statusCode) {
        return "metrics:status:minute:" + statusCode;
    }

    private static String toClientId(String apiKey, String ip) {
        return apiKey + "@" + ip;
    }

    private static long toMinuteBucket(long timestampMs) {
        return (timestampMs / 60_000L) * 60_000L;
    }

    private static long toHourBucket(long timestampMs) {
        return (timestampMs / 3_600_000L) * 3_600_000L;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.replace("|", "_").trim();
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static Map<String, Long> sortDesc(Map<String, Long> source) {
        return source.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private static String serializeEvent(long timestampMs,
                                         String apiKey,
                                         String ip,
                                         String path,
                                         int statusCode,
                                         long latencyMs,
                                         String algorithm,
                                         String decision,
                                         String reason) {
        return timestampMs + "|" + apiKey + "|" + ip + "|" + path + "|" + statusCode
                + "|" + latencyMs + "|" + algorithm + "|" + decision + "|" + reason;
    }

    private static MetricEvent parseEvent(String raw) {
        String[] parts = raw.split("\\|", 9);
        if (parts.length != 9) {
            return null;
        }
        try {
            return new MetricEvent(
                    Long.parseLong(parts[0]),
                    parts[1],
                    parts[2],
                    parts[3],
                    Integer.parseInt(parts[4]),
                    Long.parseLong(parts[5]),
                    parts[6],
                    parts[7],
                    parts[8]
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private static final class MetricEvent {
        private final long timestampMs;
        private final String apiKey;
        private final String ip;
        private final String path;
        private final int statusCode;
        private final long latencyMs;
        private final String algorithm;
        private final String decision;
        private final String reason;

        private MetricEvent(long timestampMs,
                            String apiKey,
                            String ip,
                            String path,
                            int statusCode,
                            long latencyMs,
                            String algorithm,
                            String decision,
                            String reason) {
            this.timestampMs = timestampMs;
            this.apiKey = apiKey;
            this.ip = ip;
            this.path = path;
            this.statusCode = statusCode;
            this.latencyMs = latencyMs;
            this.algorithm = algorithm;
            this.decision = decision;
            this.reason = reason;
        }
    }

    private static final class ClientRiskStats {
        private final String apiKey;
        private final String ip;
        private long total;
        private long blocked;
        private long forbidden403;
        private long rateLimited429;
        private long serverErrors5xx;

        private ClientRiskStats(String apiKey, String ip) {
            this.apiKey = apiKey;
            this.ip = ip;
        }
    }
}
