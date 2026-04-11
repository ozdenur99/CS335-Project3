package com.CS335_Project3.api_gateway;

import com.CS335_Project3.api_gateway.config.RuntimeRateLimitConfigService;
import com.CS335_Project3.api_gateway.config.TenantRateLimitConfig;
import com.CS335_Project3.api_gateway.ratelimiter.FixedWindowRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.LeakyBucketRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.SlidingWindowRateLimiterStrategy;
import com.CS335_Project3.api_gateway.ratelimiter.TokenBucketRateLimiterStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimiterTest {

    private TokenBucketRateLimiterStrategy token;
    private FixedWindowRateLimiterStrategy fixed;
    private SlidingWindowRateLimiterStrategy sliding;
    private LeakyBucketRateLimiterStrategy leaky;
    private RuntimeRateLimitConfigService runtime;
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        token = mock(TokenBucketRateLimiterStrategy.class);
        fixed = mock(FixedWindowRateLimiterStrategy.class);
        sliding = mock(SlidingWindowRateLimiterStrategy.class);
        leaky = mock(LeakyBucketRateLimiterStrategy.class);
        runtime = mock(RuntimeRateLimitConfigService.class);

        TenantRateLimitConfig cfg = new TenantRateLimitConfig();
        cfg.setDefaultLimit(5);
        cfg.setDefaultAlgorithm("token");
        when(runtime.getEffectiveConfig()).thenReturn(cfg);

        when(token.isRequestAllowed(anyString(), anyInt())).thenReturn(true);
        when(fixed.isRequestAllowed(anyString(), anyInt())).thenReturn(true);
        when(sliding.isRequestAllowed(anyString(), anyInt())).thenReturn(true);
        when(leaky.isRequestAllowed(anyString(), anyInt())).thenReturn(true);

        rateLimiter = new RateLimiter(token, fixed, sliding, leaky, cfg, runtime);
    }

    @Test
    void usesLeakyAlgorithmWhenClientMapped() {
        boolean allowed = rateLimiter.isRequestAllowed("dev-key-leaky");
        assertThat(allowed).isTrue();
        verify(leaky).isRequestAllowed("dev-key-leaky", 3);
    }

    @Test
    void fallsBackToDefaultAlgorithmAndLimitForUnknownClient() {
        boolean allowed = rateLimiter.isRequestAllowed("unknown-client");
        assertThat(allowed).isTrue();
        verify(token).isRequestAllowed("unknown-client", 5);
    }

    @Test
    void resolvesAppOverrideAlgorithm() {
        TenantRateLimitConfig cfg = new TenantRateLimitConfig();
        cfg.setDefaultAlgorithm("token");
        TenantRateLimitConfig.TenantPolicy tenant = new TenantRateLimitConfig.TenantPolicy();
        tenant.setAlgorithm("fixed");
        TenantRateLimitConfig.AppPolicy app = new TenantRateLimitConfig.AppPolicy();
        app.setAlgorithm("leaky");
        app.setLimit(2);
        tenant.getApps().put("dashboard", app);
        cfg.getTenants().put("tenant-acme", tenant);
        when(runtime.getEffectiveConfig()).thenReturn(cfg);

        assertThat(rateLimiter.getAlgorithm("dev-key-token", "tenant-acme", "dashboard")).isEqualTo("leaky");
        rateLimiter.isRequestAllowed("dev-key-token", "tenant-acme", "dashboard");
        verify(leaky).isRequestAllowed("dev-key-token|tenant-acme|dashboard", 2);
    }
}

