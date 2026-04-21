package com.CS335_Project3.api_gateway.metrics;

import com.CS335_Project3.api_gateway.logging.LogEntry;
import com.CS335_Project3.api_gateway.logging.RequestLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import java.util.concurrent.atomic.AtomicInteger;

//@RestController marks this as a REST endpoint (responses are automatically converted to JSON)
//@RequestMapping sets the base URL for all endpoints in this class to /metrics
@RestController
@RequestMapping("/metrics")
public class MetricsController {

    // we inject MetricsService, RequestLogger and BotDetector so we can read their data
    private final MetricsService metricsService;
    private final RequestLogger requestLogger;
    private final BotDetector botDetector;
    // makes HTTP calls to the backend service to get timeseries data 
    private final RestTemplate restTemplate;
    @Value("${backend.url}")
    private String backendUrl; 

    public MetricsController(MetricsService metricsService, RequestLogger requestLogger,
            BotDetector botDetector, RestTemplate restTemplate) {
        this.metricsService = metricsService;
        this.requestLogger = requestLogger;
        this.botDetector = botDetector;
        this.restTemplate = restTemplate;
    }

    // GET /metrics (returns total requests, blocked requests, allowed requests, per
    // key breakdown)
    @GetMapping
    public Map<String, Object> getMetrics() {
        return metricsService.getSnapshot();
    }

    // GET /metrics/logs (returns the last 100 logged requests)
    @GetMapping("/logs")
    public Object getLogs() {
        return requestLogger.getLogs();
    }

    // GET /metrics/logs/filter (filter logs by any combination of decision, reason,
    // apiKey, algorithm)
    @GetMapping("/logs/filter")
    public List<LogEntry> filterLogs(
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String apiKey,
            @RequestParam(required = false) String algorithm) {
        return requestLogger.getLogs().stream()
                .filter(log -> decision == null || log.getDecision().equalsIgnoreCase(decision))
                .filter(log -> reason == null || log.getReason().equalsIgnoreCase(reason))
                .filter(log -> apiKey == null || log.getApiKey().equalsIgnoreCase(apiKey))
                .filter(log -> algorithm == null || log.getAlgorithm().equalsIgnoreCase(algorithm))
                .collect(java.util.stream.Collectors.toList());
    }

    // GET /metrics/logs/export/json (downloads all logs as a JSON file)
    @GetMapping("/logs/export/json")
    public ResponseEntity<byte[]> exportLogsJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        // needed so LocalDateTime gets serialized correctly into JSON
        mapper.registerModule(new JavaTimeModule());
        byte[] json = mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(requestLogger.getLogs());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=logs.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    // GET /metrics/logs/export/csv (downloads all logs as a CSV file)
    @GetMapping("/logs/export/csv")
    public ResponseEntity<byte[]> exportLogsCsv() {
        StringBuilder csv = new StringBuilder();
        // header row with all fields including latency
        csv.append("timestamp,apiKey,ip,path,decision,reason,algorithm,latencyMs\n");
        // one row per log entry
        for (LogEntry entry : requestLogger.getLogs()) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%d\n",
                    entry.getTimestamp(),
                    entry.getApiKey(),
                    entry.getIp(),
                    entry.getPath(),
                    entry.getDecision(),
                    entry.getReason(),
                    entry.getAlgorithm(),
                    entry.getLatencyMs()));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=logs.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString().getBytes());
    }

    // GET /metrics/suspicious (shows all IPs flagged as potential bots)
    @GetMapping("/suspicious")
    public Set<String> getSuspiciousIps() {
        return botDetector.getSuspiciousIps();
    }

    // GET /metrics/suspicious/risk (shows suspicious IPs grouped by risk level)
    // HIGH = over 200 requests, MEDIUM = over 100, LOW = over 50
    @GetMapping("/suspicious/risk")
    public Map<String, List<String>> getSuspiciousIpsByRisk() {
        return botDetector.getSuspiciousIpsByRisk();
    }

    // GET /metrics/latency?apiKey=dev-key-token
    // returns p50, p95, p99 latency percentiles for a given client
    @GetMapping("/latency")
    public Map<String, Object> getLatency(@RequestParam String apiKey) {
        Map<String, Object> result = new HashMap<>();
        // percentile breakdown for this client
        result.put("apiKey", apiKey);
        result.put("percentiles", metricsService.getLatencyPercentiles(apiKey));
        result.put("statusCodes", metricsService.getStatusCodes(apiKey));
        result.put("riskScore", metricsService.getRiskScore(apiKey) + "%");
        return result;
    }

    // GET /metrics/risk (returns risk scores for all tracked clients)
    // shows how close each client is to hitting the rate limit as a percentage
    @GetMapping("/risk")
    public Map<String, Object> getAllRiskScores() {
        Map<String, Object> result = new HashMap<>();
        metricsService.getPerKeyCount()
                .forEach((key, count) -> result.put(key, metricsService.getRiskScore(key) + "%"));
        return result;
    }

    @GetMapping("/timeseries")
    public ResponseEntity<?> getTimeseries(
            @RequestParam(defaultValue = "week") String range,
            // add from and to params and forward them in the proxy URL:
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {
        String url = backendUrl + "logs/metrics/timeseries?range=" + range
                + (from != null ? "&from=" + from : "")
                + (to != null ? "&to=" + to : "");
        return ResponseEntity.ok(restTemplate.getForObject(url, List.class));
    }

    // GET /metrics/clients
    // returns a per-client summary: requests, blocked, latency percentiles, status
    // codes, risk score
    @GetMapping("/clients")
    public Map<String, Object> getClientMetrics() {
        Map<String, Object> result = new HashMap<>();
        metricsService.getPerKeyCount().forEach((apiKey, count) -> {
            Map<String, Object> clientData = new HashMap<>();
            clientData.put("totalRequests", count.get());
            clientData.put("blockedCount", metricsService.getPerKeyBlockedCount().getOrDefault(apiKey, new AtomicInteger(0)).get());
            clientData.put("latency", metricsService.getLatencyPercentiles(apiKey));
            clientData.put("statusCodes", metricsService.getStatusCodes(apiKey));
            clientData.put("riskScore", metricsService.getRiskScore(apiKey) + "%");
            result.put(apiKey, clientData);
        });
        return result;
    }

    // GET /metrics/gateway
    // returns per-gateway breakdown: requests, blocked, latency, risk scores
    // each gateway forwards its snapshot to backend every 60s via MetricsForwarder
    @GetMapping("/gateway")
    public ResponseEntity<?> getGatewayMetrics() {
        String url = backendUrl + "logs/metrics/latest";
        return ResponseEntity.ok(restTemplate.getForObject(url, Map.class));
    }

}