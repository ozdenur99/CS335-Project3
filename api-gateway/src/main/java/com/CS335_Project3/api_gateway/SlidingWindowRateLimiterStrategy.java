package com.CS335_Project3.api_gateway;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class SlidingWindowRateLimiterStrategy implements RateLimiterStrategy {

    /*
        Sliding window rate limiter.

        Concept:
        Instead of resetting fully at the end of each window like fixed window,
        this algorithm estimates usage across the current and previous window.

        This makes it fairer and smoother than fixed window.
    */

    /*
        Helper class to store each client's sliding window state
        currentCount  = requests in current window
        previousCount = requests in previous window
        windowStart   = start time of current window
    */
    private static class SlidingWindow {
        int currentCount;
        int previousCount;
        long windowStart;

        SlidingWindow(int currentCount, int previousCount, long windowStart) {
            this.currentCount = currentCount;
            this.previousCount = previousCount;
            this.windowStart = windowStart;
        }
    }

    // Store one sliding window per client
    private final Map<String, SlidingWindow> windows = new HashMap<>();

    // Window size = 1 minute
    private final long windowSizeMs = 10000;

    @Override
    public synchronized boolean isRequestAllowed(String clientId, int limit) {

        long now = System.currentTimeMillis();

        // First request from this client
        if (!windows.containsKey(clientId)) {
            windows.put(clientId, new SlidingWindow(0, 0, now));
        }

        SlidingWindow window = windows.get(clientId);

        // Check how much time has passed in the current window
        long timePassed = now - window.windowStart;

        /*
            If the full window has passed,
            shift current window into previous
            and reset current count
        */
        if (timePassed >= windowSizeMs) {
            window.previousCount = window.currentCount;
            window.currentCount = 0;
            window.windowStart = now;
            timePassed = 0;
        }

        /*
            Calculate weighted count

            The previous window contributes less over time
            as we move further into the current window
        */
        double weight = (double) (windowSizeMs - timePassed) / windowSizeMs;
        double estimatedCount = (window.previousCount * weight) + window.currentCount;

        // Allow request if estimated count is still under the limit
        if (estimatedCount < limit) {
            window.currentCount++;
            return true;
        }

        // Otherwise reject
        return false;
    }
}