package com.CS335_Project3.api_gateway.logging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class MetricsForwarder {

    private final MetricsService metricsService;
    private final RestTemplate restTemplate;

    @Value("${backend.url:http://localhost:8081/api/}")
    private String backendUrl;

    @Value("${GATEWAY_ID:gateway-1}")
    private String gatewayId;

    public MetricsForwarder(MetricsService metricsService, RestTemplate restTemplate) {
        this.metricsService = metricsService;
        this.restTemplate = restTemplate;
    }

    @Scheduled(fixedRate = 60000)
    public void forwardMetrics() {
        try {
            Map<String, Object> snapshot = metricsService.getSnapshot();
            snapshot.put("gatewayId", gatewayId);
            snapshot.put("timestamp", LocalDateTime.now().toString());
            restTemplate.postForObject(backendUrl + "metrics", snapshot, String.class);
        } catch (Exception e) {
            // fail silently, same as LogForwarder
        }
    }
}

