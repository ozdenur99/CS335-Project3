package com.CS335_Project3.api_gateway.ratelimiter;

public interface RateLimiterStrategy {
    boolean isRequestAllowed(String clientId, int limit);
}
