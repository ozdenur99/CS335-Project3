package com.CS335_Project3.api_gateway.ratelimiter;

import java.time.Duration;
import java.util.*;

import com.CS335_Project3.api_gateway.metrics.MetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DynamicAIMDRateLimiterStrategy implements RateLimiterStrategy {

    private static final String KEY_PREFIX = "rate_limit:aimd:limit:";
    private static final String STATE_PREFIX = "rate_limit:aimd:state:";
    private static final String MAX_LIMIT_PREFIX = "rate_limit:aimd:max:";

    //Max value for latency before reducing the limit
    @Value("${rate-limit.aimd.latency-threshold-ms:500}")
    private long latencyThreshold;

    //increase window by additive 1
    @Value("${rate-limit.aimd.additive-increase:1}")
    private int additiveFactor;

    //reduce window by multiplicate 2
    @Value("${rate-limit.aimd.decrease-factor:0.5}")
    private double decreaseFactor;

    private final StringRedisTemplate redisTemplate;
    private final MetricsService metricsService;
    private final FixedWindowRateLimiterStrategy baseLimiter; //Reusing Fixed window rate limiter for simplicity

    @Autowired
    public DynamicAIMDRateLimiterStrategy(StringRedisTemplate redisTemplate, MetricsService metricsService, FixedWindowRateLimiterStrategy baseLimiter) {
        this.redisTemplate = redisTemplate;
        this.metricsService = metricsService;
        this.baseLimiter = baseLimiter;
    }

    @Override
    public boolean isRequestAllowed(String clientId, int limit) {
       
        redisTemplate.opsForValue().set(MAX_LIMIT_PREFIX + clientId, String.valueOf(limit), Duration.ofMinutes(5));

        String limitKey = KEY_PREFIX + clientId;
        // 1. Fetch the dynamic limit from Redis (default to limit if not set)
        String currentLimitStr = redisTemplate.opsForValue().get(limitKey);
        int currentLimit = (currentLimitStr != null) ? Integer.parseInt(currentLimitStr) : limit;

        // 2. Delegate the actual allow/deny logic to a base strategy (e.g., Fixed or Token)
        // We use a specific AIMD state key so it doesn't clash with other algorithms
        String stateKey = STATE_PREFIX + clientId;
        return baseLimiter.isRequestAllowed(stateKey, currentLimit);
    }


    //The Controller Loop: Runs every 10 seconds to adjust limits based on latency.
    @Scheduled(fixedRateString = "${rate-limit.aimd.evaluation-period-ms:10000}")
    public void evaluateAndAdjustLimits() {
        // Get all clients currently making requests
        Set<String> activeClients = metricsService.getPerKeyCount().keySet();

        for (String clientId : activeClients) {
            Map<String, Long> percentiles = metricsService.getLatencyPercentiles(clientId);
            long p95Latency = percentiles.getOrDefault("p50", 0L); //set p50 for simplicity, but in real case, use p95

            String limitKey = KEY_PREFIX + clientId;
            String currentLimitStr = redisTemplate.opsForValue().get(limitKey);
            
            String maxLimitStr = redisTemplate.opsForValue().get(MAX_LIMIT_PREFIX + clientId);
            int maxAllowedLimit = (maxLimitStr != null) ? Integer.parseInt(maxLimitStr) : 5; // fallback

            // Assume min limit of 1 for safety
            int currentLimit = (currentLimitStr != null) ? Integer.parseInt(currentLimitStr) : maxAllowedLimit;
            int newLimit = currentLimit;

            if (p95Latency > latencyThreshold) {
                // Backend is congested -> Multiplicative Decrease
                newLimit = Math.max(1, (int) (currentLimit * decreaseFactor));
            } else if (p95Latency > 0) {
                // Backend is healthy -> Additive Increase
                newLimit = Math.min(maxAllowedLimit, currentLimit + additiveFactor);
            }

            // Save the newly calculated limit to Redis
            if (newLimit != currentLimit) {
                redisTemplate.opsForValue().set(limitKey, String.valueOf(newLimit), Duration.ofMinutes(5));
            }
        }
    }
}