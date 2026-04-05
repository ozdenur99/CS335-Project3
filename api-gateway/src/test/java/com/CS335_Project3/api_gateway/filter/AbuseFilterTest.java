package com.CS335_Project3.api_gateway.filter;

import com.CS335_Project3.api_gateway.config.AbuseDetectionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseFilterTest {

    private AbuseFilter filter;
    private AbuseDetectionConfig config;
    private Failure failure;
    private BlockedIps blockedIps;

    @BeforeEach
    void setUp() {
        config = new AbuseDetectionConfig();
        config.getFailure().setMaxFailuresPerWindow(2);
        config.getFailure().setWindowSeconds(60);
        config.setBlockDurationSeconds(300);

        failure = new Failure(config);
        blockedIps    = new BlockedIps(config);
        filter         = new AbuseFilter(failure, blockedIps);
    }

    private MockHttpServletRequest buildRequest(String apiKey, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-API-Key", apiKey);
        req.setRequestURI(uri);
        req.setRemoteAddr("10.0.0.1");
        return req;
    }

    @Test
    @DisplayName("Clean request passes through, status 200, chain not null")
    void cleanRequest_passesThrough() throws Exception {
        MockHttpServletRequest request   = buildRequest("key-alpha", "/api/test123/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain            = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Blocked IP receives 403, chain null")
    void blockedIp_returns403() throws Exception {
        blockedIps.block("10.0.0.1");

        MockHttpServletRequest request   = buildRequest("key-alpha", "/api/test123/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain            = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("blocked");
    }

    @Test
    @DisplayName("429 response does NOT trigger failure tracking — that is Sean's rate limiter")
    void responseIs429_doesNotTriggerFailureTracking() throws Exception {
        MockHttpServletRequest request   = buildRequest("key-alpha", "/api/test123/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req,
                                 jakarta.servlet.ServletResponse res)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                ((MockHttpServletResponse) res).setStatus(429);
            }
        };

        filter.doFilterInternal(request, response, chain);

        // Failure tracker should NOT have recorded anything
        assertThat(failure.getFailureCount("key-alpha")).isEqualTo(0);
    }

    @Test
    @DisplayName("Repeated 403s auto-block the IP")
    void repeated403s_autoBlockIp() throws Exception {
        // Simulate 3 requests that return 403 from backend
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = buildRequest("key-alpha", "/api/test123/notes");
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain() {
                @Override
                public void doFilter(jakarta.servlet.ServletRequest req,
                                     jakarta.servlet.ServletResponse res)
                        throws java.io.IOException, jakarta.servlet.ServletException {
                    ((MockHttpServletResponse) res).setStatus(403);
                }
            };
            filter.doFilterInternal(req, res, chain);
        }

        // After exceeding failure threshold, IP should be blocked
        assertThat(blockedIps.isBlocked("10.0.0.1")).isTrue();
    }

    @Test
    @DisplayName("Blocked IP auto-unblocks after cooldown expires")
    void blockedIp_autoUnblocksAfterCooldown() throws Exception {
        config.setBlockDurationSeconds(1); // 1 second for fast test
        blockedIps    = new BlockedIps(config);
        filter         = new AbuseFilter(failure, blockedIps);

        blockedIps.block("10.0.0.1");
        assertThat(blockedIps.isBlocked("10.0.0.1")).isTrue();

        Thread.sleep(1100); // wait for cooldown

        MockHttpServletRequest request   = buildRequest("key-alpha", "/api/test123/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain            = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        // Should pass through now — block expired
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("/health path bypasses abuse detection")
    void healthPath_bypasses() throws Exception {
        MockHttpServletRequest request   = buildRequest("key-alpha", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain            = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("/metrics path bypasses abuse detection")
    void metricsPath_bypasses() throws Exception {
        MockHttpServletRequest request   = buildRequest("key-alpha", "/metrics");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain            = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }
}