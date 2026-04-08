package com.CS335_Project3.api_gateway;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import com.CS335_Project3.api_gateway.config.TenantRateLimitConfig;
import com.CS335_Project3.api_gateway.ratelimiter.RateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.TokenBucketRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.FixedWindowRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.SlidingWindowRateLimiterStrategy;
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

    // 2a. Add config for hierarchical policies
    private final TenantRateLimitConfig tenantRateLimitConfig;

     /*
        Constructor with dependency injection for strategies and config
    */
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
        2b. Default constructor (used by tests and fallback)
        Sets tenantConfig to null; the logic safely handles this via null-checks.
    */
    public RateLimiter() {
        this.tokenBucketStrategy = new TokenBucketRateLimiterStrategy();
        this.fixedWindowStrategy = new FixedWindowRateLimiterStrategy();
        this.slidingWindowStrategy = new SlidingWindowRateLimiterStrategy();
        this.tenantRateLimitConfig = null; // No config, will use defaults

        registerStrategies();
        registerClientPolicies();
    }

    /*
       2b. Primary constructor used by Spring (dependency injection)
       @Autowired forces Spring to use the 4-arg constructor.
       This ensures 'tenantRateLimitConfig' is properly injected from application.properties,
       enabling hierarchical policy resolution (App > Tenant > Global).
       Note: The no-arg constructor is preserved solely for Unit Tests.
    */
    @Autowired
    public RateLimiter(TokenBucketRateLimiterStrategy tokenBucketStrategy,
                   FixedWindowRateLimiterStrategy fixedWindowStrategy,
                   SlidingWindowRateLimiterStrategy slidingWindowStrategy,
                   TenantRateLimitConfig tenantRateLimitConfig) {

        this.tokenBucketStrategy = tokenBucketStrategy;
        this.fixedWindowStrategy = fixedWindowStrategy;
        this.slidingWindowStrategy = slidingWindowStrategy;
        this.tenantRateLimitConfig = tenantRateLimitConfig;

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
    }

     /*
        Assign algorithms and limits to clients
    */
    private void registerClientPolicies() {
        // Standard clients
        clientAlgorithms.put("dev-key-token", "token");
        clientAlgorithms.put("dev-key-fixed", "fixed");
        clientAlgorithms.put("dev-key-sliding", "sliding");

        //limit lowered from 5 to 3 for testing purposes to trigger 429 without sending too many requests for logging
        clientLimits.put("dev-key-token", 3);
        clientLimits.put("dev-key-fixed", 3);
        clientLimits.put("dev-key-sliding", 3);

        // Business client
        clientAlgorithms.put("dev-key-business", "token");
        //limit also lowered from 10 to 6 for testing purposes to trigger 429 without sending too many requests for logging
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

    //returns which rate limiting algorithm is assigned to the given client in the logs
    //it defaults to "token" algorithm if the client is not found in the map
    public String getAlgorithm(String clientId) {
        return clientAlgorithms.getOrDefault(clientId, "token");
    }    
    /**
     * [2c] New Overloaded method for Hierarchical Scoping.
     * Resolves limits in order: App > Tenant > Global Default.
     */
    public boolean isRequestAllowed(String clientId, String tenantId, String appId) {
        // Safe access to configuration
        TenantRateLimitConfig cfg = this.tenantRateLimitConfig;
        
        // 1. Resolve Tenant Policy
        TenantRateLimitConfig.TenantPolicy tenantPolicy = (cfg != null && tenantId != null) 
            ? cfg.getTenants().get(tenantId) : null;

        // 2. Bypass check: If tenant exists but is disabled, allow all traffic
        if (tenantPolicy != null && !tenantPolicy.isEnabled()) {
            return true;
        }

        // 3. Resolve App Policy
        TenantRateLimitConfig.AppPolicy appPolicy = (tenantPolicy != null && appId != null) 
            ? tenantPolicy.getApps().get(appId) : null;

        // 4. Resolve Limit (Highest specificity wins: App > Tenant > Client/Global)
        int resolvedLimit;
        if (appPolicy != null && appPolicy.isEnabled()) {
            resolvedLimit = appPolicy.getLimit();
        } else if (tenantPolicy != null) {
            resolvedLimit = tenantPolicy.getLimit();
        } else {
            resolvedLimit = clientLimits.getOrDefault(clientId, (cfg != null) ? cfg.getDefaultLimit() : 5);
        }

        // 5. Resolve Algorithm
        String algoName;
        if (tenantPolicy != null && tenantPolicy.getAlgorithm() != null) {
            algoName = tenantPolicy.getAlgorithm();
        } else {
            algoName = clientAlgorithms.getOrDefault(clientId, (cfg != null) ? cfg.getDefaultAlgorithm() : "token");
        }

        // 6. Strategy selection
        RateLimiterStrategy strategy = strategies.getOrDefault(algoName, tokenBucketStrategy);

        /**
         * 7. Composite Key Strategy:
         * We create a unique key representing the specific bucket.
         * Example: "tenant-acme/dashboard"
         * This allows existing strategies to isolate state without code changes.
         */
        String bucketKey = (tenantId != null && appId != null) 
            ? tenantId + "/" + appId 
            : clientId;

        return strategy.isRequestAllowed(bucketKey, resolvedLimit);
    }
}
