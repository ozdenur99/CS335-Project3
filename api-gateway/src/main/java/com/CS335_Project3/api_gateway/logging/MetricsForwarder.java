package com.CS335_Project3.api_gateway.logging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
// import java.time.LocalDateTime;// 
import java.time.Instant;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import com.CS335_Project3.api_gateway.metrics.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class MetricsForwarder {

    private final MetricsService metricsService;
    private final RestTemplate restTemplate;
    private static final Logger log = LoggerFactory.getLogger(MetricsForwarder.class);

    @Value("${backend.url:http://localhost:8081/api/}")
    private String backendUrl;

    @Value("${GATEWAY_ID:gateway-1}")
    private String gatewayId;

    public MetricsForwarder(MetricsService metricsService, RestTemplate restTemplate) {
        this.metricsService = metricsService;
        this.restTemplate = restTemplate;
    }

    @Scheduled(fixedRate = 10000)
    public void forwardMetrics() {
        try {
            Map<String, Object> snapshot = metricsService.getSnapshot();
            snapshot.put("gatewayId", gatewayId);
            snapshot.put("timestamp", Instant.now().toEpochMilli());
            restTemplate.postForObject(backendUrl + "logs/metrics", snapshot, Void.class);
        } catch (Exception e) {
            log.error("MetricsForwarder failed: {}", e.getMessage());
        }
    }
}

