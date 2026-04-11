package com.CS335_Project3.api_gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RuntimeRateLimitConfigService {

    private static final String RATE_LIMIT_CONFIG_KEY = "config:rate-limit:json";

    private final StringRedisTemplate redisTemplate;
    private final TenantRateLimitConfig fallbackConfig;
    private final ObjectMapper objectMapper;

    public RuntimeRateLimitConfigService(StringRedisTemplate redisTemplate,
                                         TenantRateLimitConfig fallbackConfig,
                                         ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.fallbackConfig = fallbackConfig;
        this.objectMapper = objectMapper;
    }

    public TenantRateLimitConfig getEffectiveConfig() {
        String raw = redisTemplate.opsForValue().get(RATE_LIMIT_CONFIG_KEY);
        if (raw == null || raw.isBlank()) {
            return fallbackConfig;
        }
        try {
            TenantRateLimitConfig parsed = objectMapper.readValue(raw, TenantRateLimitConfig.class);
            return parsed == null ? fallbackConfig : parsed;
        } catch (Exception ignored) {
            return fallbackConfig;
        }
    }

    public String getEffectiveConfigJson() throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(getEffectiveConfig());
    }

    public TenantRateLimitConfig saveConfig(TenantRateLimitConfig config) throws JsonProcessingException {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        redisTemplate.opsForValue().set(RATE_LIMIT_CONFIG_KEY, json);
        return config;
    }

    public TenantRateLimitConfig saveConfigJson(String rawJson) throws JsonProcessingException {
        TenantRateLimitConfig parsed = objectMapper.readValue(rawJson, TenantRateLimitConfig.class);
        saveConfig(parsed);
        return parsed;
    }
}

