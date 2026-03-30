package com.CS335_Project3.api_gateway.metrics;

import com.CS335_Project3.api_gateway.logging.RequestLogger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

//@RestController marks this as a REST endpoint (responses are automatically converted to JSON)
//@RequestMapping sets the base URL for all endpoints in this class to /metrics
@RestController
@RequestMapping("/metrics")
public class MetricsController {
    //we inject MetricsService and RequestLogger so we can read their data
    private final MetricsService metricsService;
    private final RequestLogger requestLogger;

    public MetricsController(MetricsService metricsService, RequestLogger requestLogger) {
        this.metricsService  = metricsService;
        this.requestLogger   = requestLogger;
    }

    //we input GET /metrics
    //to return total requests, blocked requests, allowed requests
    @GetMapping
    public Map<String, Object> getMetrics() {
        return metricsService.getSnapshot();
    }

    // GET /metrics/logs → returns the last 100 logged requests
    @GetMapping("/logs")
    public Object getLogs() {
        return requestLogger.getLogs();
    }
}