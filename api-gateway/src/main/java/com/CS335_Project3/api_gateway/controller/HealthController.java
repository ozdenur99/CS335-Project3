package com.CS335_Project3.api_gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private static final long START_TIME = System.currentTimeMillis();

    @Value("${GATEWAY_ID:gateway-1}")
    private String gatewayId;

    @Value("${backend.url}")
    private String backendUrl;

    private final RedisTemplate<String, String> redisTemplate;
    private final RestTemplate restTemplate;

    public HealthController(RedisTemplate<String, String> redisTemplate, RestTemplate restTemplate) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {

        String redisStatus = checkRedis();
        String backendStatus = checkBackend();

        String overall = (redisStatus.equals("UP") && backendStatus.equals("UP")) ? "UP" : "DEGRADED";
        // LinkedHashMap preserves field order in the JSON response (looks cleaner)
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", overall);
        result.put("service", "API Gateway");
        result.put("gatewayId", gatewayId);
        result.put("redis", redisStatus);
        result.put("backend", backendStatus);
        result.put("uptimeSeconds", (System.currentTimeMillis() - START_TIME) / 1000);

        int httpStatus = overall.equals("UP") ? 200 : 207;
        return ResponseEntity.status(httpStatus).body(result);
    }

    // checkRedis() does a real Redis read — if it throws, Redis is down
    private String checkRedis() {
        try {
            redisTemplate.opsForValue().get("health:ping");
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    // checkBackend() makes a simple GET request to the backend,
    // if it throws, backend is down
    private String checkBackend() {
        try {
            restTemplate.exchange(
                    backendUrl.replace("/api/", "/"),
                    org.springframework.http.HttpMethod.GET,
                    null,

                    // Void.class tells RestTemplate to ignore the response body completely,
                    // any HTTP response at all means backend is UP. 
                    // only a connection refused or timeout means DOWN.
                    Void.class);
            return "UP";
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return "UP"; // backend responded (even with error) — it's reachable
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
