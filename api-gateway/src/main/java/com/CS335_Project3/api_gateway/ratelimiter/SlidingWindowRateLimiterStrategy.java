package com.CS335_Project3.api_gateway.ratelimiter;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Component
public class SlidingWindowRateLimiterStrategy implements RateLimiterStrategy {

    /*
        Sliding window rate limiter.

        Concept:
        Instead of resetting fully at the end of each window like fixed window,
        this algorithm estimates usage across the current and previous window.

        This makes it fairer and smoother than fixed window.

        This version stores all state in Redis so it works across
        multiple instances (e.g. Docker containers).
    */

    /*
        Redis key format:
        rate_limit:sliding:<clientId>

        Each key stores a hash with:
        - currentCount  = requests in current window
        - previousCount = requests in previous window
        - windowStart   = start time of current window
    */
    private static final String KEY_PREFIX = "rate_limit:sliding:";
    private static final String CURRENT_FIELD = "currentCount";
    private static final String PREVIOUS_FIELD = "previousCount";
    private static final String WINDOW_START_FIELD = "windowStart";

    /*
        Window size (10 seconds for testing/demo purposes)
    */
    private final long windowSizeMs = 10000;

    /*
        TTL for Redis key.

        Slightly longer than window size so both current and previous
        window data are preserved.

        After inactivity, the key is automatically removed.
    */
    private static final Duration WINDOW_TTL = Duration.ofSeconds(20);

    /*
        Redis template for interacting with Redis
    */
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public SlidingWindowRateLimiterStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /*
        Checks whether a request from a given client is allowed or not.

        NOTE:
        No longer synchronized — Redis is now the shared state.
    */
    @Override
    public boolean isRequestAllowed(String clientId, int limit) {

        String key = KEY_PREFIX + clientId;

        // Get current time
        long now = System.currentTimeMillis();

        /*
            Read existing sliding window state from Redis
        */
        Object currentObj = redisTemplate.opsForHash().get(key, CURRENT_FIELD);
        Object previousObj = redisTemplate.opsForHash().get(key, PREVIOUS_FIELD);
        Object windowStartObj = redisTemplate.opsForHash().get(key, WINDOW_START_FIELD);

        int currentCount;
        int previousCount;
        long windowStart;

        /*
            If this is the user's first request,
            initialise a new sliding window
        */
        if (currentObj == null || previousObj == null || windowStartObj == null) {
            currentCount = 0;
            previousCount = 0;
            windowStart = now;

            // WRITE INITIAL STATE IMMEDIATELY
            redisTemplate.opsForHash().put(key, CURRENT_FIELD, "0");
            redisTemplate.opsForHash().put(key, PREVIOUS_FIELD, "0");
            redisTemplate.opsForHash().put(key, WINDOW_START_FIELD, String.valueOf(now));
            redisTemplate.expire(key, WINDOW_TTL);
        }else {
            currentCount = Integer.parseInt(currentObj.toString());
            previousCount = Integer.parseInt(previousObj.toString());
            windowStart = Long.parseLong(windowStartObj.toString());
        }

        /*
            Calculate how much time has passed in the current window
        */
        long timePassed = now - windowStart;

        /*
            If the full window has passed,
            shift current window into previous
            and reset current count
        */
        if (timePassed >= windowSizeMs) {
            previousCount = currentCount;
            currentCount = 0;
            windowStart = now;
            timePassed = 0;
        }

        /*
            Calculate weighted count

            The previous window contributes less over time
            as we move further into the current window
        */
        double weight = (double) (windowSizeMs - timePassed) / windowSizeMs;
        double estimatedCount = (previousCount * weight) + currentCount;

        boolean allowed = false;

        /*
            Allow request if estimated count is still under the limit
        */
        if (estimatedCount < limit) {
            currentCount++;
            allowed = true;
        }

        /*
            Save updated sliding window state back to Redis
        */
        redisTemplate.opsForHash().put(key, CURRENT_FIELD, String.valueOf(currentCount));
        redisTemplate.opsForHash().put(key, PREVIOUS_FIELD, String.valueOf(previousCount));
        redisTemplate.opsForHash().put(key, WINDOW_START_FIELD, String.valueOf(windowStart));

        /*
            Set expiry so inactive clients are cleaned up automatically
        */
        redisTemplate.expire(key, WINDOW_TTL);

        return allowed;
    }
}