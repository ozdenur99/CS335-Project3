package com.CS335_Project3.api_gateway;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import com.CS335_Project3.api_gateway.ratelimiter.RateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.TokenBucketRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.FixedWindowRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.SlidingWindowRateLimiterStrategy;

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
        Default constructor (used by tests and fallback)
    */
    public RateLimiter() {
        this.tokenBucketStrategy = new TokenBucketRateLimiterStrategy();
        this.fixedWindowStrategy = new FixedWindowRateLimiterStrategy();
        this.slidingWindowStrategy = new SlidingWindowRateLimiterStrategy();

        registerStrategies();
        registerClientPolicies();
    }

    /*
        Constructor used by Spring (dependency injection)
    */
    public RateLimiter(TokenBucketRateLimiterStrategy tokenBucketStrategy,
                   FixedWindowRateLimiterStrategy fixedWindowStrategy,
                   SlidingWindowRateLimiterStrategy slidingWindowStrategy) {

        this.tokenBucketStrategy = tokenBucketStrategy;
        this.fixedWindowStrategy = fixedWindowStrategy;
        this.slidingWindowStrategy = slidingWindowStrategy;

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

        clientLimits.put("dev-key-token", 5);
        clientLimits.put("dev-key-fixed", 5);
        clientLimits.put("dev-key-sliding", 5);

        // Business client
        clientAlgorithms.put("dev-key-business", "token");
        clientLimits.put("dev-key-business", 10);
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
}
