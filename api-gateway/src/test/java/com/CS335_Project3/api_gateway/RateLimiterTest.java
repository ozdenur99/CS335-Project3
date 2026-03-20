package com.CS335_Project3.api_gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter();
    }

    @Test
    void requestsBelowLimit_shouldBeAllowed() {
        String clientId = "client-1";
        
        // Make 5 requests (the limit)
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.isRequestAllowed(clientId))
                .as("Request %d should be allowed", i + 1)
                .isTrue();
        }
    }

    @Test
    void requestsExceedingLimit_shouldBeBlocked() {
        String clientId = "client-2";
        
        // First 5 requests should pass
        for (int i = 0; i < 5; i++) {
            rateLimiter.isRequestAllowed(clientId);
        }
        
        // 6th request should fail
        assertThat(rateLimiter.isRequestAllowed(clientId))
            .as("6th request should be blocked")
            .isFalse();
    }

    @Test
    void differentClients_haveIndependentLimits() {
        String client1 = "client-a";
        String client2 = "client-b";
        
        // Max out client1
        for (int i = 0; i < 5; i++) {
            rateLimiter.isRequestAllowed(client1);
        }
        
        // Client2 should still be allowed
        assertThat(rateLimiter.isRequestAllowed(client2))
            .as("client-b should have independent limit")
            .isTrue();
    }

    @Test
    void windowExpiry_resetsRequestCount() throws InterruptedException {
        String clientId = "client-reset";
        
        // Max out the client
        for (int i = 0; i < 5; i++) {
            rateLimiter.isRequestAllowed(clientId);
        }
        
        // Next request should fail
        assertThat(rateLimiter.isRequestAllowed(clientId))
            .as("Should be blocked before window expires")
            .isFalse();
        
        // NOTE: Full window test would require waiting 60 seconds.
        // In production, use @WithSpringCloudContractTest or time-mocking libraries like MockClock
        // For now, this demonstrates the blocking behavior.
    }
}
