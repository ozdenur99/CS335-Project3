package com.CS335_Project3.api_gateway.controller;

import com.CS335_Project3.api_gateway.RateLimiter;
import com.CS335_Project3.api_gateway.config.TenantRateLimitConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/config")
public class ConfigController {

    private final TenantRateLimitConfig tenantRateLimitConfig;
    private final RateLimiter rateLimiter;
    private final StringRedisTemplate redis;

    public ConfigController(TenantRateLimitConfig tenantRateLimitConfig,
                            RateLimiter rateLimiter,
                            StringRedisTemplate redis) {
        this.tenantRateLimitConfig = tenantRateLimitConfig;
        this.rateLimiter           = rateLimiter;
        this.redis                 = redis;
    }

    // GET /admin/config
    // Shows current in-memory tenant + app policies
    @GetMapping
    public Map<String, Object> getConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("defaultLimit",     tenantRateLimitConfig.getDefaultLimit());
        result.put("defaultAlgorithm", tenantRateLimitConfig.getDefaultAlgorithm());
        result.put("tenants",          tenantRateLimitConfig.getTenants());
        return result;
    }

    // POST /admin/config
    // Body: { "tenant": "tenant-acme", "app": "dashboard", "algorithm": "sliding", "limit": 10 }
    // app is optional — omit to change the tenant-level policy
    @PostMapping
    public Map<String, String> updateConfig(@RequestBody Map<String, Object> body) {
        String tenant    = (String)  body.get("tenant");
        String app       = (String)  body.get("app");       // optional
        String algorithm = (String)  body.get("algorithm"); // optional
        Integer limit    = body.get("limit") != null
                           ? Integer.parseInt(body.get("limit").toString()) : null;

        if (tenant == null) {
            return Map.of("status", "error", "message", "tenant is required");
        }

        TenantRateLimitConfig.TenantPolicy tenantPolicy =
                tenantRateLimitConfig.getTenants().get(tenant);

        if (tenantPolicy == null) {
            return Map.of("status", "error", "message", "tenant not found: " + tenant);
        }

        if (app != null) {
            // App-level change
            TenantRateLimitConfig.AppPolicy appPolicy =
                    tenantPolicy.getApps().get(app);
            if (appPolicy == null) {
                return Map.of("status", "error", "message", "app not found: " + app);
            }
            if (algorithm != null) appPolicy.setAlgorithm(algorithm);
            if (limit     != null) appPolicy.setLimit(limit);

            // Write to Redis so the other gateway picks it up
            if (algorithm != null)
                redis.opsForValue().set("config:" + tenant + "/" + app + ":algorithm", algorithm);
            if (limit != null)
                redis.opsForValue().set("config:" + tenant + "/" + app + ":limit", limit.toString());

        } else {
            // Tenant-level change
            if (algorithm != null) tenantPolicy.setAlgorithm(algorithm);
            if (limit     != null) tenantPolicy.setLimit(limit);

            if (algorithm != null)
                redis.opsForValue().set("config:" + tenant + ":algorithm", algorithm);
            if (limit != null)
                redis.opsForValue().set("config:" + tenant + ":limit", limit.toString());
        }

        // Reload so this gateway's RateLimiter sees the update immediately
        rateLimiter.reloadConfig();

        return Map.of("status", "ok", "message", "config updated");
    }
}
