package com.CS335_Project3.api_gateway;

import com.CS335_Project3.api_gateway.config.TenantRateLimitConfig;
import com.CS335_Project3.api_gateway.ratelimiter.FixedWindowRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.SlidingWindowRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.TokenBucketRateLimiterStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Hierarchical Rate Limiting (Tenant + App Scoping).
 * Verifies that buckets are isolated and policies resolve with correct priority.
 */
class TenantScopedRateLimiterTest {

    private RateLimiter rateLimiter;
    private TenantRateLimitConfig config;

    @BeforeEach
    void setUp() {
        this.config = buildConfig();
        this.rateLimiter = buildRateLimiter(config);
    }

    /**
     * Helper to build a manual configuration matching the test requirements.
     */
    private TenantRateLimitConfig buildConfig() {
        TenantRateLimitConfig cfg = new TenantRateLimitConfig();
        cfg.setDefaultLimit(5);
        cfg.setDefaultAlgorithm("token");

        // Setup Tenant-Acme: Limit 3, App Dashboard: Limit 2
        TenantRateLimitConfig.TenantPolicy acme = new TenantRateLimitConfig.TenantPolicy();
        acme.setLimit(3);
        acme.setEnabled(true);

        TenantRateLimitConfig.AppPolicy dashboard = new TenantRateLimitConfig.AppPolicy();
        dashboard.setLimit(2);
        dashboard.setEnabled(true);
        acme.getApps().put("dashboard", dashboard);

        cfg.getTenants().put("tenant-acme", acme);

        // Setup Tenant-Beta: Disabled (Bypass)
        TenantRateLimitConfig.TenantPolicy beta = new TenantRateLimitConfig.TenantPolicy();
        beta.setEnabled(false);
        cfg.getTenants().put("tenant-beta", beta);

        return cfg;
    }

    private RateLimiter buildRateLimiter(TenantRateLimitConfig cfg) {
        return new RateLimiter(
            new TokenBucketRateLimiterStrategy(),
            new FixedWindowRateLimiterStrategy(),
            new SlidingWindowRateLimiterStrategy(),
            cfg
        );
    }

    @Test
    void twoTenantsWithSameApiKey_haveIndependentBuckets() {
        String apiKey = "dev-key-token";

        // Exhaust tenant-acme (Limit 3)
        for (int i = 0; i < 3; i++) {
            rateLimiter.isRequestAllowed(apiKey, "tenant-acme", "default");
        }
        assertThat(rateLimiter.isRequestAllowed(apiKey, "tenant-acme", "default")).isFalse();

        // tenant-beta should still be allowed (independent bucket)
        assertThat(rateLimiter.isRequestAllowed(apiKey, "tenant-beta", "default")).isTrue();
    }

    @Test
    void missingTenantHeader_fallsBackToDefaultBucket() {
        String apiKey = "dev-key-token";

        // Uses defaultLimit = 5
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.isRequestAllowed(apiKey, "default", "default")).isTrue();
        } 
        assertThat(rateLimiter.isRequestAllowed(apiKey, "default", "default")).isFalse();
    }

    @Test
    void disabledTenant_alwaysAllowed() {
        String apiKey = "dev-key-token";

        // tenant-beta.enabled = false, should allow 20+ requests
        for (int i = 0; i < 20; i++) {
            assertThat(rateLimiter.isRequestAllowed(apiKey, "tenant-beta", "any-app")).isTrue();
        }
    }

    @Test
    void perAppLimit_appliesCorrectly() {
        String apiKey = "dev-key-token";

        // dashboard limit = 2
        assertThat(rateLimiter.isRequestAllowed(apiKey, "tenant-acme", "dashboard")).isTrue();
        assertThat(rateLimiter.isRequestAllowed(apiKey, "tenant-acme", "dashboard")).isTrue();
        
        // 3rd request should fail
        assertThat(rateLimiter.isRequestAllowed(apiKey, "tenant-acme", "dashboard")).isFalse();
    }

    @Test
    void disabledApp_fallsThroughToTenantLimit() {
        String apiKey = "dev-key-token";

        // Disable the dashboard app policy dynamically
        config.getTenants().get("tenant-acme").getApps().get("dashboard").setEnabled(false);

        // Should now use Tenant-Acme limit (3) instead of App limit (2)
        assertThat(rateLimiter.isRequestAllowed(apiKey, "tenant-acme", "dashboard")).isTrue();
        assertThat(rateLimiter.isRequestAllowed(apiKey, "tenant-acme", "dashboard")).isTrue();
        assertThat(rateLimiter.isRequestAllowed(apiKey, "tenant-acme", "dashboard")).isTrue();
        
        // 4th request should fail (Tenant limit exhausted)
        assertThat(rateLimiter.isRequestAllowed(apiKey, "tenant-acme", "dashboard")).isFalse();
    }
}