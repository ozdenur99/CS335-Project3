package com.CS335_Project3.api_gateway;

import com.CS335_Project3.api_gateway.config.TenantRateLimitConfig;
import com.CS335_Project3.api_gateway.ratelimiter.TokenBucketRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.FixedWindowRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.SlidingWindowRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.LeakyBucketRateLimiterStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Map;

class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        TokenBucketRateLimiterStrategy token = mock(TokenBucketRateLimiterStrategy.class);
        FixedWindowRateLimiterStrategy fixed = mock(FixedWindowRateLimiterStrategy.class);
        SlidingWindowRateLimiterStrategy sliding = mock(SlidingWindowRateLimiterStrategy.class);
        LeakyBucketRateLimiterStrategy leaky = mock(LeakyBucketRateLimiterStrategy.class);  

        TenantRateLimitConfig config = new TenantRateLimitConfig();
        
        config.setDefaultLimit(3);
        config.setDefaultAlgorithm("token");

        // per-clientId counter: allow up to `limit` calls, then block
        Map<String, Integer> counts = new java.util.HashMap<>();
        when(token.isRequestAllowed(anyString(), anyInt())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            int limit = inv.getArgument(1);
            int next = counts.getOrDefault(key, 0) + 1;
            counts.put(key, next);
            return next <= limit;
        });

        rateLimiter = new RateLimiter(token, fixed, sliding, leaky, config);
    }

    @Test
    void requestsBelowLimit_shouldBeAllowed() {
        String clientId = "client-1";

        // Make 3 requests (the limit)
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.isRequestAllowed(clientId))
                    .as("Request %d should be allowed", i + 1)
                    .isTrue();
        }
    }

    @Test
    void requestsExceedingLimit_shouldBeBlocked() {
        String clientId = "client-2";

        // First 3 requests should pass
        for (int i = 0; i < 3; i++) {
            rateLimiter.isRequestAllowed(clientId);
        }

        // 4th request should fail
        assertThat(rateLimiter.isRequestAllowed(clientId))
                .as("4th request should be blocked")
                .isFalse();
    }

    @Test
    void differentClients_haveIndependentLimits() {
        String client1 = "client-a";
        String client2 = "client-b";

        // Max out client1
        for (int i = 0; i < 3; i++) {
            rateLimiter.isRequestAllowed(client1);
        }

        // Client2 should still be allowed
        assertThat(rateLimiter.isRequestAllowed(client2))
                .as("client-b should have independent limit")
                .isTrue();
    }

    // @Test
    // void windowExpiry_resetsRequestCount() throws InterruptedException {
    // String clientId = "client-reset";

    // // Max out the client
    // for (int i = 0; i < 3; i++) {
    // rateLimiter.isRequestAllowed(clientId);
    // }

    // // Next request should fail
    // assertThat(rateLimiter.isRequestAllowed(clientId))
    // .as("Should be blocked before window expires")
    // .isFalse();

    // // NOTE: Full window test would require waiting 60 seconds.
    // // In production, use @WithSpringCloudContractTest or time-mocking libraries
    // // like MockClock
    // // For now, this demonstrates the blocking behavior.
    // }
}
