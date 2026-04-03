package com.CS335_Project3.api_gateway;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class FixedWindowRateLimiterStrategy implements RateLimiterStrategy {

    private static class Window {
        int requestCount;
        long windowStart;

        Window(int requestCount, long windowStart) {
            this.requestCount = requestCount;
            this.windowStart = windowStart;
        }
    }

    // Store one window per client
    private Map<String, Window> windows = new HashMap<>();

    // Window size = 10 seconds
    private final long windowSizeMs = 10000;

    @Override
    public synchronized boolean isRequestAllowed(String clientId, int limit) {

        long now = System.currentTimeMillis();

        // First request from this client
        if (!windows.containsKey(clientId)) {
            windows.put(clientId, new Window(0, now));
        }

        Window window = windows.get(clientId);

        // If current window has expired, reset it
        if (now - window.windowStart >= windowSizeMs) {
            window.requestCount = 0;
            window.windowStart = now;
        }

        // Allow request if still under the limit
        if (window.requestCount < limit) {
            window.requestCount++;
            return true;
        }

        // Otherwise reject
        return false;
    }
}