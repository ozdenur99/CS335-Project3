package com.CS335_Project3.api_gateway;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import com.CS335_Project3.api_gateway.config.TenantRateLimitConfig;
import com.CS335_Project3.api_gateway.ratelimiter.RateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.TokenBucketRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.FixedWindowRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.SlidingWindowRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.LeakyBucketRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.DynamicAIMDRateLimiterStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Main entry point for rate limiting logic.
 * Manages multiple selective algorithms and resolves
 * Hierarchical policies (App > Tenant > Client >Global).
 */
@Component
public class RateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    /*
     * This class acts as the main entry point for rate limiting.
     * 
     * It holds multiple rate limiting strategies and chooses
     * which one to use based on the client.
     * 
     * This allows different clients to use different algorithms.
     */

    // Strategy instances
    private final TokenBucketRateLimiterStrategy tokenBucketStrategy;
    private final FixedWindowRateLimiterStrategy fixedWindowStrategy;
    private final SlidingWindowRateLimiterStrategy slidingWindowStrategy;
    private final LeakyBucketRateLimiterStrategy leakyBucketStrategy;
    private final DynamicAIMDRateLimiterStrategy dynamicAIMDRateLimiterStrategy;

    // Config for hierarchical policies
    private final TenantRateLimitConfig tenantRateLimitConfig;

    private final StringRedisTemplate redis;

    /*
     * This map stores which algorithm each client should use
     * 
     * Key = clientId (API key)
     * Value = algorithm name
     */
    private final Map<String, String> clientAlgorithms = new HashMap<>();

    /*
     * This map stores the actual strategies
     * 
     * Key = algorithm name
     * Value = strategy implementation
     * 
     * This removes the need for switch statements
     */
    private final Map<String, RateLimiterStrategy> strategies = new HashMap<>();

    /*
     * This map stores each client's request limit / bucket size
     * 
     * Key = clientId (API key)
     * Value = max allowed requests / capacity
     */
    private final Map<String, Integer> clientLimits = new HashMap<>();

    // Tracks whether Redis is currently reachable
    // switch to in-memory fallback mode if Redis goes down
    // Create the "Circuit Breaker" to prevent 500 errors.
    private volatile boolean redisAvailable = true;

    // In-memory fallback counters used when Redis is down
    // Key = bucketKey, Value = request count in current window
    private final ConcurrentHashMap<String, AtomicInteger> fallbackCounters = new ConcurrentHashMap<>();

    /*
     * Primary constructor used by Spring (dependency injection)
     * 
     * Spring injects each strategy here, including the Redis-backed
     * fixed window strategy.
     * 
     * This is now the only constructor needed.
     */
    @Autowired
    public RateLimiter(TokenBucketRateLimiterStrategy tokenBucketStrategy,
            FixedWindowRateLimiterStrategy fixedWindowStrategy,
            SlidingWindowRateLimiterStrategy slidingWindowStrategy,
            LeakyBucketRateLimiterStrategy leakyBucketStrategy,
            DynamicAIMDRateLimiterStrategy dynamicAIMDRateLimiterStrategy,
            TenantRateLimitConfig tenantRateLimitConfig,
            StringRedisTemplate redis) {

        this.tokenBucketStrategy = tokenBucketStrategy;
        this.fixedWindowStrategy = fixedWindowStrategy;
        this.slidingWindowStrategy = slidingWindowStrategy;
        this.leakyBucketStrategy = leakyBucketStrategy;
        this.dynamicAIMDRateLimiterStrategy = dynamicAIMDRateLimiterStrategy;
        this.tenantRateLimitConfig = tenantRateLimitConfig;
        this.redis = redis;

        registerStrategies();
        registerClientPolicies();
    }

    /*
     * Register all available rate limiting strategies
     */
    private void registerStrategies() {
        strategies.put("token", tokenBucketStrategy);
        strategies.put("fixed", fixedWindowStrategy);
        strategies.put("sliding", slidingWindowStrategy);
        strategies.put("leaky", leakyBucketStrategy);
        strategies.put("dynamic", dynamicAIMDRateLimiterStrategy);

    }

    /*
     * Assign algorithms and limits to clients
     */
    private void registerClientPolicies() {
        // Standard clients
        clientAlgorithms.put("dev-key-token", "token");
        clientAlgorithms.put("dev-key-fixed", "fixed");
        clientAlgorithms.put("dev-key-sliding", "sliding");
        clientAlgorithms.put("dev-key-leaky", "leaky");
        clientAlgorithms.put("dev-key-dynamic", "dynamic");

        // limit lowered from 5 to 3 for testing purposes
        // to trigger 429 without sending too many requests for logging
        clientLimits.put("dev-key-token", 3);
        clientLimits.put("dev-key-fixed", 3);
        clientLimits.put("dev-key-sliding", 3);
        clientLimits.put("dev-key-leaky", 3);
        clientLimits.put("dev-key-dynamic", 10);

        // Business client
        clientAlgorithms.put("dev-key-business", "token");

        // limit also lowered from 10 to 6 for testing purposes

        // to trigger 429 without sending too many requests for logging
        clientLimits.put("dev-key-business", 6);
        // Tenant/App scoped keys — algorithm and limit are fallback only
        // Real policy comes from tenant/app config (application.properties or
        // ConfigController)
        clientAlgorithms.put("key-acme-dashboard", "token");
        clientAlgorithms.put("key-acme-api", "fixed");
        clientAlgorithms.put("key-beta-dashboard", "sliding");
        clientAlgorithms.put("key-beta-api", "leaky");
        clientAlgorithms.put("key-enterprise-dashboard", "fixed");
        clientAlgorithms.put("key-enterprise-api", "token");

        clientLimits.put("key-acme-dashboard", 10);
        clientLimits.put("key-acme-api", 20);
        clientLimits.put("key-beta-dashboard", 15);
        clientLimits.put("key-beta-api", 25);
        clientLimits.put("key-enterprise-dashboard", 20);
        clientLimits.put("key-enterprise-api", 50);

    }

    /*
     * Called by API key filter
     * 
     * Determines which algorithm to use for the client
     * and delegates the request to that strategy
     */
    public boolean isRequestAllowed(String clientId) {

        // Get algorithm for this client (default = token bucket)
        String algo = clientAlgorithms.getOrDefault(clientId, "token");

        // Get the correct strategy (fallback = token bucket)
        RateLimiterStrategy strategy = strategies.getOrDefault(algo, tokenBucketStrategy);

        // Get limit for this client (default = 5)
        int limit = clientLimits.getOrDefault(clientId, 5);

        try {
            boolean allowed = strategy.isRequestAllowed(clientId, limit);
            redisAvailable = true;
            return allowed;
        } catch (Exception e) {
            if (redisAvailable) {
                log.warn("[RateLimiter] Redis unavailable, switching to in-memory fallback: {}", e.getMessage());
                redisAvailable = false;
            }
            return fallbackAllow(clientId, limit);
        }
    }

    // returns which rate limiting algorithm is assigned to the given client in the
    // logs
    // it defaults to "token" algorithm if the client is not found in the map
    public String getAlgorithm(String clientId) {
        return clientAlgorithms.getOrDefault(clientId, "token");
    }

    // Resolves algorithm hierarchically: App > Tenant > Client > Global.
    // Ensures logs show the actual algorithm that was enforced.
    public String getResolvedAlgorithm(String clientId, String tenantId, String appId) {
        TenantRateLimitConfig cfg = this.tenantRateLimitConfig;

        TenantRateLimitConfig.TenantPolicy tenantPolicy = (cfg != null && tenantId != null)
                ? cfg.getTenants().get(tenantId)
                : null;

        TenantRateLimitConfig.AppPolicy appPolicy = (tenantPolicy != null && appId != null)
                ? tenantPolicy.getApps().get(appId)
                : null;

        if (appPolicy != null && appPolicy.getAlgorithm() != null)
            return appPolicy.getAlgorithm();
        if (tenantPolicy != null && tenantPolicy.getAlgorithm() != null)
            return tenantPolicy.getAlgorithm();
        return clientAlgorithms.getOrDefault(clientId, (cfg != null) ? cfg.getDefaultAlgorithm() : "token");
    }

    // exposes the strategies map so ConfigController can validate algorithm names
    public Map<String, RateLimiterStrategy> getStrategies() {
        return strategies;
    }

    // clientLimits is a private map inside RateLimiter, we expose it through a
    // public method.
    // this allows ConfigController to read the each client's limit when config
    // updates.
    public int getClientLimit(String clientId) {
        return clientLimits.getOrDefault(clientId, tenantRateLimitConfig.getDefaultLimit());
    }

    /**
     * New overloaded method for hierarchical scoping.
     * Resolves limits in order: App > Tenant > Global Default.
     */
    public boolean isRequestAllowed(String clientId, String tenantId, String appId) {
        // Safe access to configuration
        TenantRateLimitConfig cfg = this.tenantRateLimitConfig;

        // 1. Resolve Tenant Policy
        TenantRateLimitConfig.TenantPolicy tenantPolicy = (cfg != null && tenantId != null)
                ? cfg.getTenants().get(tenantId)
                : null;

        // 2. Bypass check: If tenant exists but is disabled, allow all traffic
        if (tenantPolicy != null && !tenantPolicy.isEnabled()) {
            return true;
        }

        // 3. Resolve App Policy
        TenantRateLimitConfig.AppPolicy appPolicy = (tenantPolicy != null && appId != null)
                ? tenantPolicy.getApps().get(appId)
                : null;

        // 4. Resolve Limit (Highest specificity wins: App > Tenant > Client/Global)
        int resolvedLimit;
        if (appPolicy != null && appPolicy.isEnabled()) {
            resolvedLimit = appPolicy.getLimit();
        } else if (tenantPolicy != null) {
            resolvedLimit = tenantPolicy.getLimit();
        } else {
            resolvedLimit = clientLimits.getOrDefault(clientId, (cfg != null) ? cfg.getDefaultLimit() : 5);
        }

        // 5. Resolve Algorithm: App > Tenant > Client > Global
        String algoName;
        if (appPolicy != null && appPolicy.getAlgorithm() != null) {
            algoName = appPolicy.getAlgorithm();
        } else if (tenantPolicy != null && tenantPolicy.getAlgorithm() != null) {
            algoName = tenantPolicy.getAlgorithm();
        } else {
            algoName = clientAlgorithms.getOrDefault(clientId, (cfg != null) ? cfg.getDefaultAlgorithm() : "token");
        }

        // 6. Strategy selection
        RateLimiterStrategy strategy = strategies.getOrDefault(algoName, tokenBucketStrategy);

        /*
         * 7. Composite Key Strategy:
         * We create a unique key representing the specific bucket.
         * Example: "tenant-acme/dashboard"
         * This allows existing strategies to isolate state without code changes.
         */
        String bucketKey;
        if (tenantId != null && !tenantId.isBlank() && appId != null && !appId.isBlank()) {
            bucketKey = tenantId + "/" + appId; // app layer
        } else if (tenantId != null && !tenantId.isBlank()) {
            bucketKey = tenantId; // tenant layer — shared across all clients of that tenant
        } else {
            bucketKey = clientId; // client layer
        }

        try {
            // Attempt the standard distributed check (Redis)
            boolean allowed = strategy.isRequestAllowed(bucketKey, resolvedLimit);
            redisAvailable = true; // Connection is good
            return allowed;
        } catch (Exception e) {
            // If Redis fails, don't crash the request with a 500 error.
            if (redisAvailable) {
                // log once, not on every request
                log.warn("[RateLimiter] Redis unavailable, switching to in-memory fallback: {}", e.getMessage());
                redisAvailable = false;
            }
            // Switch to local memory counting so we still enforce limits
            return fallbackAllow(bucketKey, resolvedLimit);
        }

    }

    /*
     * Reads Redis for any live overrides written by ConfigController after each
     * POST /admin/config.
     * 
     * @PostConstruct = runs at startup, so gateway2 can see changes made by
     * gateway1.
     * This allows dynamic updates without needing to restart the gateway.
     * Wrapped in try-catch to prevent startup crashes if Redis is offline.
     */
    @PostConstruct
    public void reloadConfig() {
        try {
            tenantRateLimitConfig.getTenants().forEach((tenantId, tenantPolicy) -> {
                // Tenant-level overrides — one Hash read instead of two String reads
                Map<Object, Object> tenantOverrides = redis.opsForHash().entries("config:" + tenantId);
                String tenantAlgo = (String) tenantOverrides.get("algorithm");
                String tenantLimit = (String) tenantOverrides.get("limit");
                if (tenantAlgo != null)
                    tenantPolicy.setAlgorithm(tenantAlgo);
                if (tenantLimit != null)
                    tenantPolicy.setLimit(Integer.parseInt(tenantLimit));

                // App-level overrides — one Hash read per app
                tenantPolicy.getApps().forEach((appId, appPolicy) -> {
                    Map<Object, Object> appOverrides = redis.opsForHash().entries("config:" + tenantId + "/" + appId);
                    String appAlgo = (String) appOverrides.get("algorithm");
                    String appLimit = (String) appOverrides.get("limit");

                    if (appAlgo != null) {
                        appPolicy.setAlgorithm(appAlgo);
                    }
                    if (appLimit != null) {
                        appPolicy.setLimit(Integer.parseInt(appLimit));
                    }
                });
            });
            log.info("[RateLimiter] Configuration successfully synchronized with Redis.");
        } catch (Exception e) {
            // This prevents the startup crash if Redis is unavailable,
            // allowing the gateway to run with local defaults.
            log.warn("[RateLimiter] Could not connect to Redis at startup. Falling back to local defaults. Reason: {}",
                    e.getMessage());
        }
    }

    /*
     * Fallback counter used when Redis is unreachable.
     * Still enforces rate limits per key using in-memory counts,
     * so the gateway stays secure even without Redis.
     * Trade-off: limits are per-node only (not shared across gateway1/gateway2).
     */
    private boolean fallbackAllow(String key, int limit) {
        int count = fallbackCounters
                .computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();
        return count <= limit;
    }

    /*
     * Runs every 10 seconds to check if Redis has come back online.
     * When Redis recovers: clears fallback counters (stale in-memory counts)
     * and flips redisAvailable back to true so strategies resume using Redis.
     * Logs only on state change (up→down or down→up) to avoid log spam.
     */
    @Scheduled(fixedDelay = 10000)
    public void checkRedisHealth() {
        try {
            redis.hasKey("health-ping"); // lightweight read, no side effects
            if (!redisAvailable) {
                log.info("[RateLimiter] Redis connection restored. Clearing fallback counters.");
                fallbackCounters.clear(); // reset stale counts so Redis takes over cleanly
                redisAvailable = true;
            }
        } catch (Exception e) {
            if (redisAvailable) {
                log.warn("[RateLimiter] Redis health check failed: {}", e.getMessage());
                redisAvailable = false;
            }
        }
    }
}