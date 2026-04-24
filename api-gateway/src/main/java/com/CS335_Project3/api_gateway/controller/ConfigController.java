package com.CS335_Project3.api_gateway.controller;

import com.CS335_Project3.api_gateway.RateLimiter;
import com.CS335_Project3.api_gateway.config.AuditEntry;
import com.CS335_Project3.api_gateway.config.TenantRateLimitConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    // Jackson ObjectMapper serializes AuditEntry → JSON string for Redis storage
    // and deserializes JSON string → AuditEntry when reading back
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Redis List key — all gateways push to and read from the same list
    // so audit log is shared and survives restarts
    private static final String AUDIT_KEY = "config:audit";

    // max entries to keep in Redis — older entries are trimmed automatically
    private static final long AUDIT_MAX_SIZE = 100;

    /**
     * Constructor injection — Spring sees this constructor and automatically
     * provides the three beans. No @Autowired needed when there's only one
     * constructor.
     */
    @Value("${GATEWAY_ID:gateway-1}")
    private String gatewayId;

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
        // containsKey() prevents searching for tenants if the user sent a typo like
        // "tokken"
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

            // Capture old values BEFORE changing anything
            // This is what shows up in the audit log as "what it was before"
            String oldAlgorithm = appPolicy.getAlgorithm();
            int oldLimit = appPolicy.getLimit();

            // Modify the in-memory object directly.
            // Only set fields that were actually provided in the request (null check).
            if (algorithm != null)
                appPolicy.setAlgorithm(algorithm);
            if (limit != null)
                appPolicy.setLimit(limit);

            // Write as Redis Hash — one key per scope, fields inside
            String appHashKey = "config:" + tenant + "/" + app;
            if (algorithm != null)
                redis.opsForHash().put(appHashKey, "algorithm", algorithm);
            if (limit != null)
                redis.opsForHash().put(appHashKey, "limit", limit.toString());
            // Write audit entry to Redis after the change succeeds
            writeAudit(tenant, app, oldAlgorithm, algorithm, oldLimit, limit);

        } else {
            // Tenant-level change
            String oldAlgorithm = tenantPolicy.getAlgorithm();
            int oldLimit = tenantPolicy.getLimit();

            // Tenant-level change
            if (algorithm != null)
                tenantPolicy.setAlgorithm(algorithm);
            if (limit != null)
                tenantPolicy.setLimit(limit);

            // Redis key format for tenant level: "config:tenant-acme:algorithm"
            // Notice no "/" — that distinguishes tenant-level from app-level keys.
            String tenantHashKey = "config:" + tenant;
            if (algorithm != null)
                redis.opsForHash().put(tenantHashKey, "algorithm", algorithm);
            if (limit != null)
                redis.opsForHash().put(tenantHashKey, "limit", limit.toString());
            writeAudit(tenant, null, oldAlgorithm, algorithm, oldLimit, limit);
        }

        // Reload so this gateway's RateLimiter sees the update immediately
        // Notify ALL gateways via Pub/Sub — each listener calls reloadConfig()
        // instantly
        redis.convertAndSend("config-reload", "update");

        return Map.of("status", "ok", "message", "config updated");
    }

    /**
     * GET /admin/config/audit
     * Returns the last 100 config changes across all gateways, newest first.
     * Reads from Redis so it persists across restarts and includes changes
     * made by gateway-2 as well.
     */
    @GetMapping("/audit")
    public List<AuditEntry> getAuditLog() {
        List<String> raw = redis.opsForList().range(AUDIT_KEY, 0, AUDIT_MAX_SIZE - 1);
        List<AuditEntry> entries = new ArrayList<>();
        if (raw == null)
            return entries;

        for (String json : raw) {
            try {
                entries.add(objectMapper.readValue(json, AuditEntry.class));
            } catch (Exception e) {
                // skip corrupted entries rather than failing the whole response
            }
        }
        return entries;
    }

    /**
     * Serializes an AuditEntry to JSON and pushes it to the Redis List.
     * LPUSH puts newest entries at index 0 (front of list).
     * LTRIM keeps the list capped at AUDIT_MAX_SIZE so Redis doesn't grow forever.
     *
     * newAlgorithm and newLimit are null when only one field was changed —
     * the audit entry only records what actually changed.
     */
    private void writeAudit(String tenant, String app,
            String oldAlgorithm, String newAlgorithm,
            int oldLimit, Integer newLimit) {
        try {
            AuditEntry entry = new AuditEntry(
                    Instant.now().toString(),
                    tenant,
                    app,
                    oldAlgorithm,
                    newAlgorithm, // null if only limit changed
                    oldLimit,
                    newLimit, // null if only algorithm changed
                    gatewayId);
            String json = objectMapper.writeValueAsString(entry);

            // LPUSH = Left Push — newest entry goes to the front (index 0)
            redis.opsForList().leftPush(AUDIT_KEY, json);

            // LTRIM keeps only the 100 most recent entries
            // index 0 = newest, index 99 = oldest kept
            redis.opsForList().trim(AUDIT_KEY, 0, AUDIT_MAX_SIZE - 1);

        } catch (Exception e) {
            // audit failure must never break the actual config update
        }
    }
}
