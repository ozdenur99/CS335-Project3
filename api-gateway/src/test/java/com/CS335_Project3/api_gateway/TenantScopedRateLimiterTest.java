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

class TenantScopedRateLimiterTest {

    private TokenBucketRateLimiterStrategy token;
    private FixedWindowRateLimiterStrategy fixed;
    private SlidingWindowRateLimiterStrategy sliding;
    private LeakyBucketRateLimiterStrategy leaky;
    private RuntimeRateLimitConfigService runtime;
    private TenantRateLimitConfig config;
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        token = mock(TokenBucketRateLimiterStrategy.class);
        fixed = mock(FixedWindowRateLimiterStrategy.class);
        sliding = mock(SlidingWindowRateLimiterStrategy.class);
        leaky = mock(LeakyBucketRateLimiterStrategy.class);
        runtime = mock(RuntimeRateLimitConfigService.class);
        when(token.isRequestAllowed(anyString(), anyInt())).thenReturn(true);
        when(fixed.isRequestAllowed(anyString(), anyInt())).thenReturn(true);
        when(sliding.isRequestAllowed(anyString(), anyInt())).thenReturn(true);
        when(leaky.isRequestAllowed(anyString(), anyInt())).thenReturn(true);

        config = buildConfig();
        when(runtime.getEffectiveConfig()).thenReturn(config);
        rateLimiter = new RateLimiter(token, fixed, sliding, leaky, config, runtime);
    }

    private TenantRateLimitConfig buildConfig() {
        TenantRateLimitConfig cfg = new TenantRateLimitConfig();
        cfg.setDefaultLimit(5);
        cfg.setDefaultAlgorithm("token");

        TenantRateLimitConfig.ClientPolicy client = new TenantRateLimitConfig.ClientPolicy();
        client.setLimit(4);
        client.setAlgorithm("fixed");
        cfg.getClients().put("dev-key-token", client);

        TenantRateLimitConfig.TenantPolicy acme = new TenantRateLimitConfig.TenantPolicy();
        acme.setLimit(3);
        acme.setAlgorithm("sliding");
        acme.setEnabled(true);

        TenantRateLimitConfig.AppPolicy dashboard = new TenantRateLimitConfig.AppPolicy();
        dashboard.setLimit(2);
        dashboard.setAlgorithm("leaky");
        dashboard.setEnabled(true);
        acme.getApps().put("dashboard", dashboard);
        cfg.getTenants().put("tenant-acme", acme);

        TenantRateLimitConfig.TenantPolicy beta = new TenantRateLimitConfig.TenantPolicy();
        beta.setEnabled(false);
        cfg.getTenants().put("tenant-beta", beta);
        return cfg;
    }

    @Test
    void appPolicyHasHighestPriority() {
        assertThat(rateLimiter.getAlgorithm("dev-key-token", "tenant-acme", "dashboard")).isEqualTo("leaky");
        rateLimiter.isRequestAllowed("dev-key-token", "tenant-acme", "dashboard");
        verify(leaky).isRequestAllowed("dev-key-token|tenant-acme|dashboard", 2);
    }

    @Test
    void tenantPolicyBeatsClientPolicyWhenNoAppPolicy() {
        assertThat(rateLimiter.getAlgorithm("dev-key-token", "tenant-acme", "other-app")).isEqualTo("sliding");
        rateLimiter.isRequestAllowed("dev-key-token", "tenant-acme", "other-app");
        verify(sliding).isRequestAllowed("dev-key-token|tenant-acme|other-app", 3);
    }

    @Test
    void disabledTenantBypassesLimiter() {
        assertThat(rateLimiter.isRequestAllowed("dev-key-token", "tenant-beta", "dashboard")).isTrue();
    }

    @Test
    void clientPolicyBeatsGlobalWhenNoTenantPolicy() {
        assertThat(rateLimiter.getAlgorithm("dev-key-token", "default", "default")).isEqualTo("fixed");
        rateLimiter.isRequestAllowed("dev-key-token", "default", "default");
        verify(fixed).isRequestAllowed("dev-key-token|default|default", 4);
    }
}

