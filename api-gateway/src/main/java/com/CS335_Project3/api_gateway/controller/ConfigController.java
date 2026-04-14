package com.CS335_Project3.api_gateway.controller;

import com.CS335_Project3.api_gateway.RateLimiter;
import com.CS335_Project3.api_gateway.config.TenantRateLimitConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for runtime configuration changes.
 * * WHY @RestController and not @Controller?
 * * @RestController = @Controller + @ResponseBody on every method.
 * Every method return value is automatically serialized to JSON.
 * 
 * Exposes /admin/config endpoints so you can change rate limit policies
 * without restarting the server. Changes apply instantly to both gateways
 * via Redis as the shared source of truth.
 */
@RestController

// @RequestMapping sets the base URL prefix for all methods in this class.
// Both GET and POST will start with /admin/config.
@RequestMapping("/admin/config")
public class ConfigController {

    // 1) tenantRateLimitConfig: Local Memory.
    // Ensures the current node applies changes instantly without latency.
    private final TenantRateLimitConfig tenantRateLimitConfig;
    // 2) rateLimiter: Logic Engine.
    // after updating config, we call reloadConfig()
    // so the RateLimiter's internal state also reflects the new policy.
    // re-initializes internal state to reflect new limits or algorithms.
    private final RateLimiter rateLimiter;
    // 3) redis: Global State(Distributed Bridge).
    // we write changes to Redis so the 2nd gateway instance.
    // it synchronizes the update to all other gateway nodes in the cluster,
    // ensuring "Gateway-2" behaves exactly like "Gateway-1."
    private final StringRedisTemplate redis;

    /**
     * Constructor injection — Spring sees this constructor and automatically
     * provides the three beans. No @Autowired needed when there's only one
     * constructor.
     */
    public ConfigController(TenantRateLimitConfig tenantRateLimitConfig,
            RateLimiter rateLimiter,
            StringRedisTemplate redis) {
        this.tenantRateLimitConfig = tenantRateLimitConfig;
        this.rateLimiter = rateLimiter;
        this.redis = redis;
    }

    /**
     * GET /admin/config
     * Shows current in-memory tenant + app policies
     * 
     * @GetMapping means this method handles HTTP GET requests to /admin/config.
     *             The return value (a Map) is automatically converted to a JSON
     *             object by Spring.
     */
    @GetMapping
    public Map<String, Object> getConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("defaultLimit", tenantRateLimitConfig.getDefaultLimit());
        result.put("defaultAlgorithm", tenantRateLimitConfig.getDefaultAlgorithm());
        result.put("tenants", tenantRateLimitConfig.getTenants());
        return result;
    }

    /**
     * POST /admin/config
     * Updates a tenant-level OR app-level policy at runtime, no restart needed.
     * 
     * @PostMapping means this method handles HTTP POST requests to /admin/config.
     * @RequestBody tells Spring to read the HTTP request body and deserialize it
     */
    @PostMapping
    public Map<String, String> updateConfig(@RequestBody Map<String, Object> body) {

        // Extract fields from the request JSON body.
        // body.get() returns Object, so we cast to the expected type.
        String tenant = (String) body.get("tenant");
        String app = (String) body.get("app"); // optional
        String algorithm = (String) body.get("algorithm"); // optional

        // limit needs special handling: JSON numbers come in as Integer or Double,
        // so we call toString() first then parseInt() to be safe.
        Integer limit = body.get("limit") != null
                ? Integer.parseInt(body.get("limit").toString())
                : null;

        // 1. Check basic requirements
        if (tenant == null) {
            return Map.of("status", "error", "message", "tenant is required");
        }

        // 2. Validate algorithm name immediately (Fail Fast)
        // containsKey() prevents searching for tenants if the user sent a typo like "tokken"
        if (algorithm != null && !rateLimiter.getStrategies().containsKey(algorithm)) {
            return Map.of("status", "error", "message", "Invalid algorithm: " + algorithm);
        }

        // 3. Look up the Tenant in the in-memory config.
        TenantRateLimitConfig.TenantPolicy tenantPolicy = tenantRateLimitConfig.getTenants().get(tenant);

        if (tenantPolicy == null) {
            return Map.of("status", "error", "message", "tenant not found: " + tenant);
        }

        if (app != null) {
            // App-level change
            TenantRateLimitConfig.AppPolicy appPolicy = tenantPolicy.getApps().get(app);
            if (appPolicy == null) {
                return Map.of("status", "error", "message", "app not found: " + app);
            }

            // Modify the in-memory object directly.
            // Only set fields that were actually provided in the request (null check).
            if (algorithm != null)
                appPolicy.setAlgorithm(algorithm);
            if (limit != null)
                appPolicy.setLimit(limit);

            // Write to Redis so gateway-2 can read the updated value.
            // Key format: "config:tenant-acme/dashboard:algorithm"
            // This is a simple string key-value — not a sorted set or hash.
            if (algorithm != null)
                redis.opsForValue().set("config:" + tenant + "/" + app + ":algorithm", algorithm);
            if (limit != null)
                redis.opsForValue().set("config:" + tenant + "/" + app + ":limit", limit.toString());

        } else {
            // Tenant-level change
            if (algorithm != null)
                tenantPolicy.setAlgorithm(algorithm);
            if (limit != null)
                tenantPolicy.setLimit(limit);

            // Redis key format for tenant level: "config:tenant-acme:algorithm"
            // Notice no "/" — that distinguishes tenant-level from app-level keys.
            if (algorithm != null)
                redis.opsForValue().set("config:" + tenant + ":algorithm", algorithm);
            if (limit != null)
                redis.opsForValue().set("config:" + tenant + ":limit", limit.toString());
        }

        // Reload so this gateway's RateLimiter sees the update immediately
        // Tell THIS gateway's RateLimiter to re-read the updated TenantRateLimitConfig.
        rateLimiter.reloadConfig();

        return Map.of("status", "ok", "message", "config updated");
    }
}
