package com.CS335_Project3.api_gateway;

public interface RateLimiterStrategy {
    boolean isRequestAllowed(String clientId, int limit);
}
