package com.CS335_Project3.api_gateway.filter;

import com.CS335_Project3.api_gateway.config.AbuseDetectionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXPECTED OUTCOMES:
 * - Clean request           → 200, chain.getRequest() not null (passed through)
 * - Blocked IP              → 403, chain.getRequest() null (blocked)
 * - Spike detected          → 429, chain.getRequest() null (blocked)
 * - Excluded path (/health) → 200, chain.getRequest() not null (bypassed)
 */
class AbuseFilterTest {

    private AbuseFilter filter;
    private AbuseDetectionConfig config;
    private Spike spike;
    private Failure failure;
    private BlockedIps blockedIps;

    @BeforeEach
    void setUp() {
        config = new AbuseDetectionConfig();
        // Set low thresholds so tests don't need hundreds of requests
        config.getSpike().setMaxRequestsPerWindow(3);
        config.getSpike().setWindowSeconds(10);
        config.getFailure().setMaxFailuresPerWindow(2);
        config.getFailure().setWindowSeconds(60);

        spike  = new Spike(config);
        failure = new Failure(config);
        blockedIps    = new BlockedIps();
        filter         = new AbuseFilter(spike, failure, blockedIps);
    }

    // Helper ---

    private MockHttpServletRequest buildRequest(String apiKey, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-API-Key", apiKey);
        req.setRequestURI(uri);
        req.setRemoteAddr("10.0.0.1");
        return req;
    }

    // Tests ---

    @Test
    @DisplayName("Clean request passes through — response 200, chain not null")
    void cleanRequest_passesThrough() throws Exception {
        MockHttpServletRequest request   = buildRequest("key-alpha", "/api/test123/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain            = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull(); // request passed through
    }

    @Test
    @DisplayName("Blocked IP receives 403 — chain null (blocked)")
    void blockedIp_returns403() throws Exception {
        blockedIps.block("10.0.0.1");

        MockHttpServletRequest request   = buildRequest("key-alpha", "/api/test123/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain            = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull(); // request was blocked
        assertThat(response.getContentAsString()).contains("blocked");
    }

    @Test
    @DisplayName("Spike: 4th request gets 429 — chain null, IP auto-blocked")
    void spikeDetected_returns429() throws Exception {
        // Send 3 requests (threshold is 3) — all should pass
        for (int i = 1; i <= 3; i++) {
            MockHttpServletRequest req  = buildRequest("key-alpha", "/api/test123/notes");
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain ch         = new MockFilterChain();
            filter.doFilterInternal(req, res, ch);
            assertThat(res.getStatus()).isEqualTo(200);
            assertThat(ch.getRequest()).isNotNull();
        }

        // 4th request exceeds threshold — should be blocked
        MockHttpServletRequest request4  = buildRequest("key-alpha", "/api/test123/notes");
        MockHttpServletResponse response4 = new MockHttpServletResponse();
        MockFilterChain chain4            = new MockFilterChain();

        filter.doFilterInternal(request4, response4, chain4);

        assertThat(response4.getStatus()).isEqualTo(429);
        assertThat(chain4.getRequest()).isNull();
        assertThat(response4.getContentAsString()).contains("Abnormal request rate");
        assertThat(blockedIps.isBlocked("10.0.0.1")).isTrue(); // IP auto-blocked
    }

    @Test
    @DisplayName("/health path bypasses all abuse detection")
    void healthPath_bypasses() throws Exception {
        MockHttpServletRequest request   = buildRequest("key-alpha", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain            = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("/metrics path bypasses all abuse detection")
    void metricsPath_bypasses() throws Exception {
        MockHttpServletRequest request   = buildRequest("key-alpha", "/metrics");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain            = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("No API key falls back to IP for tracking")
    void noApiKey_usesIpForTracking() throws Exception {
        // Send 3 requests with no API key (tracking falls back to IP 10.0.0.1)
        for (int i = 1; i <= 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI("/api/test123/notes");
            req.setRemoteAddr("10.0.0.1");
            // No X-API-Key header added
            filter.doFilterInternal(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        // 4th request should be spiked (IP tracked, no key)
        MockHttpServletRequest req4 = new MockHttpServletRequest();
        req4.setRequestURI("/api/test123/notes");
        req4.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res4 = new MockHttpServletResponse();

        filter.doFilterInternal(req4, res4, new MockFilterChain());

        assertThat(res4.getStatus()).isEqualTo(429);
    }
}