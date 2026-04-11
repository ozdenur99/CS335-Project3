package com.CS335_Project3.api_gateway;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import com.CS335_Project3.api_gateway.config.TenantRateLimitConfig;
import com.CS335_Project3.api_gateway.config.RuntimeRateLimitConfigService;
import com.CS335_Project3.api_gateway.ratelimiter.RateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.TokenBucketRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.FixedWindowRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.SlidingWindowRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.LeakyBucketRateLimiterStrategy;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Main entry point for rate limiting logic.
 * Manages multiple strategies and resolves 
 * hierarchical policies (App > Tenant > Global).
 */
@Component
public class RateLimiter {

    /*
        This class acts as the main entry point for rate limiting.

        It holds multiple rate limiting strategies and chooses
        which one to use based on the client.

        This allows different clients to use different algorithms.
    */

    // Strategy instances
    private final TokenBucketRateLimiterStrategy tokenBucketStrategy;
    private final FixedWindowRateLimiterStrategy fixedWindowStrategy;
    private final SlidingWindowRateLimiterStrategy slidingWindowStrategy;
    private final LeakyBucketRateLimiterStrategy leakyBucketStrategy;

    // Config for hierarchical policies
    private final TenantRateLimitConfig tenantRateLimitConfig;
    private final RuntimeRateLimitConfigService runtimeRateLimitConfigService;

    /*
        This map stores which algorithm each client should use

        Key   = clientId (API key)
        Value = algorithm name
    */
    private final Map<String, String> clientAlgorithms = new HashMap<>();

    /*
        This map stores the actual strategies

        Key   = algorithm name
        Value = strategy implementation

        This removes the need for switch statements
    */
    private final Map<String, RateLimiterStrategy> strategies = new HashMap<>();

    /*
        This map stores each client's request limit / bucket size

        Key   = clientId (API key)
        Value = max allowed requests / capacity
    */
    private final Map<String, Integer> clientLimits = new HashMap<>();

    /*
        Primary constructor used by Spring (dependency injection)

        Spring injects each strategy here, including the Redis-backed
        fixed window strategy.

        This is now the only constructor needed.
    */
    @Autowired
    public RateLimiter(TokenBucketRateLimiterStrategy tokenBucketStrategy,
                       FixedWindowRateLimiterStrategy fixedWindowStrategy,
                       SlidingWindowRateLimiterStrategy slidingWindowStrategy,
                       LeakyBucketRateLimiterStrategy leakyBucketStrategy,
                       TenantRateLimitConfig tenantRateLimitConfig,
                       RuntimeRateLimitConfigService runtimeRateLimitConfigService) {

        this.tokenBucketStrategy = tokenBucketStrategy;
        this.fixedWindowStrategy = fixedWindowStrategy;
        this.slidingWindowStrategy = slidingWindowStrategy;
        this.leakyBucketStrategy = leakyBucketStrategy;
        this.tenantRateLimitConfig = tenantRateLimitConfig;
        this.runtimeRateLimitConfigService = runtimeRateLimitConfigService;

        registerStrategies();
        registerClientPolicies();
    }

    /*
        Register all available rate limiting strategies
    */
    private void registerStrategies() {
        strategies.put("token", tokenBucketStrategy);
        strategies.put("fixed", fixedWindowStrategy);
        strategies.put("sliding", slidingWindowStrategy);
        strategies.put("leaky", leakyBucketStrategy);
    }

    /*
        Assign algorithms and limits to clients
    */
    private void registerClientPolicies() {
        // Standard clients
        clientAlgorithms.put("dev-key-token", "token");
        clientAlgorithms.put("dev-key-fixed", "fixed");
        clientAlgorithms.put("dev-key-sliding", "sliding");
        clientAlgorithms.put("dev-key-leaky", "leaky");

        // limit lowered from 5 to 3 for testing purposes
        // to trigger 429 without sending too many requests for logging
        clientLimits.put("dev-key-token", 3);
        clientLimits.put("dev-key-fixed", 3);
        clientLimits.put("dev-key-sliding", 3);
        clientLimits.put("dev-key-leaky", 3);

        // Business client
        clientAlgorithms.put("dev-key-business", "token");

        // limit also lowered from 10 to 6 for testing purposes
        // to trigger 429 without sending too many requests for logging
        clientLimits.put("dev-key-business", 6);
    }

    /*
        Called by API key filter

        Determines which algorithm to use for the client
        and delegates the request to that strategy
    */
    public boolean isRequestAllowed(String clientId) {

        // Get algorithm for this client (default = token bucket)
        String algo = clientAlgorithms.getOrDefault(clientId, "token");

        // Get the correct strategy (fallback = token bucket)
        RateLimiterStrategy strategy = strategies.getOrDefault(algo, tokenBucketStrategy);

        // Get limit for this client (default = 5)
        int limit = clientLimits.getOrDefault(clientId, 5);

        // Delegate request
        return strategy.isRequestAllowed(clientId, limit);
    }

    // returns which rate limiting algorithm is assigned to the given client in the logs
    // it defaults to "token" algorithm if the client is not found in the map
    public String getAlgorithm(String clientId) {
        return clientAlgorithms.getOrDefault(clientId, "token");
    }

    public String getAlgorithm(String clientId, String tenantId, String appId) {
        TenantRateLimitConfig cfg = resolveConfig();
        String normalizedClient = normalize(clientId, "UNKNOWN");
        String normalizedTenant = normalize(tenantId, "default");
        String normalizedApp = normalize(appId, "default");

        TenantRateLimitConfig.TenantPolicy tenantPolicy = cfg.getTenants().get(normalizedTenant);
        TenantRateLimitConfig.AppPolicy appPolicy = tenantPolicy == null ? null : tenantPolicy.getApps().get(normalizedApp);
        TenantRateLimitConfig.ClientPolicy clientPolicy = cfg.getClients().get(normalizedClient);

        if (appPolicy != null && appPolicy.getAlgorithm() != null && !appPolicy.getAlgorithm().isBlank()) {
            return appPolicy.getAlgorithm().toLowerCase();
        }
        if (tenantPolicy != null && tenantPolicy.getAlgorithm() != null && !tenantPolicy.getAlgorithm().isBlank()) {
            return tenantPolicy.getAlgorithm().toLowerCase();
        }
        if (clientPolicy != null && clientPolicy.getAlgorithm() != null && !clientPolicy.getAlgorithm().isBlank()) {
            return clientPolicy.getAlgorithm().toLowerCase();
        }
        if (cfg.getDefaultAlgorithm() != null && !cfg.getDefaultAlgorithm().isBlank()) {
            return cfg.getDefaultAlgorithm().toLowerCase();
        }
        return clientAlgorithms.getOrDefault(normalizedClient, "token");
    }

    /**
     * New overloaded method for hierarchical scoping.
     * Resolves limits in order: App > Tenant > Global Default.
     */
    public boolean isRequestAllowed(String clientId, String tenantId, String appId) {
        // Safe access to configuration
        TenantRateLimitConfig cfg = resolveConfig();
        String normalizedClient = normalize(clientId, "UNKNOWN");
        String normalizedTenant = normalize(tenantId, "default");
        String normalizedApp = normalize(appId, "default");
        
        // 1. Resolve Tenant Policy
        TenantRateLimitConfig.TenantPolicy tenantPolicy = cfg.getTenants().get(normalizedTenant);

        // 2. Bypass check: If tenant exists but is disabled, allow all traffic
        if (tenantPolicy != null && !tenantPolicy.isEnabled()) {
            return true;
        }

        // 3. Resolve App Policy
        TenantRateLimitConfig.AppPolicy appPolicy = (tenantPolicy == null) ? null : tenantPolicy.getApps().get(normalizedApp);
        TenantRateLimitConfig.ClientPolicy clientPolicy = cfg.getClients().get(normalizedClient);

        if (clientPolicy != null && !clientPolicy.isEnabled()) {
            return false;
        }

        // 4. Resolve Limit (Highest specificity wins: App > Tenant > Client/Global)
        int resolvedLimit;
        if (appPolicy != null && appPolicy.isEnabled()) {
            resolvedLimit = appPolicy.getLimit();
        } else if (tenantPolicy != null) {
            resolvedLimit = tenantPolicy.getLimit();
        } else if (clientPolicy != null) {
            resolvedLimit = clientPolicy.getLimit();
        } else {
            resolvedLimit = clientLimits.getOrDefault(normalizedClient, cfg.getDefaultLimit());
        }

        // 5. Resolve Algorithm
        String algoName = getAlgorithm(normalizedClient, normalizedTenant, normalizedApp);

        // 6. Strategy selection
        RateLimiterStrategy strategy = strategies.getOrDefault(algoName, tokenBucketStrategy);

        /*
         * 7. Composite Key Strategy:
         * We create a unique key representing the specific bucket.
         * Example: "tenant-acme/dashboard"
         * This allows existing strategies to isolate state without code changes.
         */
        String bucketKey = normalizedClient + "|" + normalizedTenant + "|" + normalizedApp;

        return strategy.isRequestAllowed(bucketKey, resolvedLimit);
    }

    private TenantRateLimitConfig resolveConfig() {
        if (runtimeRateLimitConfigService != null) {
            TenantRateLimitConfig config = runtimeRateLimitConfigService.getEffectiveConfig();
            if (config != null) {
                return config;
            }
        }
        return tenantRateLimitConfig == null ? new TenantRateLimitConfig() : tenantRateLimitConfig;
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().toLowerCase();
    }
}
