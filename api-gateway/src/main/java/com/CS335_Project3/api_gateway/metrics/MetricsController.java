package com.CS335_Project3.api_gateway.metrics;

import com.CS335_Project3.api_gateway.logging.RequestLogger;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.CS335_Project3.api_gateway.logging.LogEntry;
import com.CS335_Project3.api_gateway.metrics.BotDetector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.Set;
import java.util.List;

//@RestController marks this as a REST endpoint (responses are automatically converted to JSON)
//@RequestMapping sets the base URL for all endpoints in this class to /metrics
@RestController
@RequestMapping("/metrics")
public class MetricsController {
    //we inject MetricsService, RequestLogger and botDetector so we can read their data
    private final MetricsService metricsService;
    private final RequestLogger requestLogger;
    private final BotDetector botDetector;

    public MetricsController(MetricsService metricsService, RequestLogger requestLogger, BotDetector botDetector) {
        this.metricsService = metricsService;
        this.requestLogger  = requestLogger;
        this.botDetector    = botDetector;
    }

    //we input GET /metrics
    //to return total requests, blocked requests, allowed requests
    @GetMapping
    public Map<String, Object> getMetrics() {
        return metricsService.getSnapshot();
    }

    //GET /metrics/logs (returns the last 100 logged requests)
    @GetMapping("/logs")
    public Object getLogs() {
        return requestLogger.getLogs();
    }

    //GET /metrics/logs/filter?decision=BLOCKED (403)
    //GET /metrics/logs/filter?reason=rate_limit_exceeded (429)
    //GET /metrics/logs/filter?apiKey=dev-key-alpha
    //GET /metrics/logs/filter?algorithm=token
    @GetMapping("/logs/filter")
    public List<LogEntry> filterLogs(
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String apiKey,
            @RequestParam(required = false) String algorithm) {

        return requestLogger.getLogs().stream()
                .filter(log -> decision  == null || log.getDecision().equalsIgnoreCase(decision))
                .filter(log -> reason    == null || log.getReason().equalsIgnoreCase(reason))
                .filter(log -> apiKey    == null || log.getApiKey().equalsIgnoreCase(apiKey))
                .filter(log -> algorithm == null || log.getAlgorithm().equalsIgnoreCase(algorithm))
                .collect(java.util.stream.Collectors.toList());
    }

    //GET /metrics/logs/export/json (downloads all logs as a JSON file)
    @GetMapping("/logs/export/json")
    public ResponseEntity<byte[]> exportLogsJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        //needed so LocalDateTime gets serialized correctly into JSON
        mapper.registerModule(new JavaTimeModule());
        byte[] json = mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(requestLogger.getLogs());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=logs.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    //GET /metrics/logs/export/csv (downloads all logs as a CSV file)
    @GetMapping("/logs/export/csv")
    public ResponseEntity<byte[]> exportLogsCsv() {
        StringBuilder csv = new StringBuilder();
        //header row
        csv.append("timestamp,apiKey,ip,path,decision,reason,algorithm\n");

        //1 row per log
        for (LogEntry entry : requestLogger.getLogs()) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s\n",
                    entry.getTimestamp(),
                    entry.getApiKey(),
                    entry.getIp(),
                    entry.getPath(),
                    entry.getDecision(),
                    entry.getReason(),
                    entry.getAlgorithm()
            ));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=logs.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString().getBytes());
    }

    //GET /metrics/suspicious (shows all IPs flagged as potential bots)
    @GetMapping("/suspicious")
    public Set<String> getSuspiciousIps() {
        return botDetector.getSuspiciousIps();
    }

    //GET /metrics/dashboard/overall-trend?minutes=60
    @GetMapping("/dashboard/overall-trend")
    public Map<String, Object> getOverallTrend(@RequestParam(defaultValue = "60") int minutes) {
        return metricsService.getOverallTrend(minutes);
    }

    //GET /metrics/dashboard/client-trend?apiKey=dev-key-token&ip=127.0.0.1&minutes=60
    @GetMapping("/dashboard/client-trend")
    public Map<String, Object> getClientTrend(@RequestParam String apiKey,
                                              @RequestParam String ip,
                                              @RequestParam(defaultValue = "60") int minutes) {
        return metricsService.getClientTrend(apiKey, ip, minutes);
    }

    //GET /metrics/dashboard/status-trend?minutes=60
    @GetMapping("/dashboard/status-trend")
    public Map<String, Object> getStatusTrend(@RequestParam(defaultValue = "60") int minutes) {
        return metricsService.getStatusTrend(minutes);
    }

    //GET /metrics/dashboard/status-distribution?minutes=60
    @GetMapping("/dashboard/status-distribution")
    public Map<String, Object> getStatusDistribution(@RequestParam(defaultValue = "60") int minutes) {
        return metricsService.getStatusDistribution(minutes);
    }

    //GET /metrics/dashboard/latency-trend?minutes=60
    @GetMapping("/dashboard/latency-trend")
    public Map<String, Object> getLatencyTrend(@RequestParam(defaultValue = "60") int minutes) {
        return metricsService.getLatencyTrend(minutes);
    }

    //GET /metrics/dashboard/algorithm-blocking?minutes=60
    @GetMapping("/dashboard/algorithm-blocking")
    public Map<String, Object> getAlgorithmBlocking(@RequestParam(defaultValue = "60") int minutes) {
        return metricsService.getAlgorithmBlockingComparison(minutes);
    }

    //GET /metrics/dashboard/risk-leaderboard?minutes=30&limit=20
    @GetMapping("/dashboard/risk-leaderboard")
    public Map<String, Object> getRiskLeaderboard(@RequestParam(defaultValue = "30") int minutes,
                                                  @RequestParam(defaultValue = "20") int limit) {
        return metricsService.getRiskLeaderboard(minutes, limit);
    }

    //GET /metrics/dashboard/client-detail?apiKey=dev-key-token&ip=127.0.0.1&minutes=60&limit=50
    @GetMapping("/dashboard/client-detail")
    public Map<String, Object> getClientDetail(@RequestParam String apiKey,
                                               @RequestParam String ip,
                                               @RequestParam(defaultValue = "60") int minutes,
                                               @RequestParam(defaultValue = "50") int limit) {
        return metricsService.getClientDetail(apiKey, ip, minutes, limit);
    }
}
