package com.CS335_Project3.api_gateway.filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.CS335_Project3.api_gateway.RateLimiter;
import com.CS335_Project3.api_gateway.config.ApiKeyConfig;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyFilterTest {

    private ApiKeyFilter filter;

    @BeforeEach
    void setUp() {
        ApiKeyConfig config = new ApiKeyConfig();
        // Update to match actual keys used in application.properties
        config.setApiKeys(List.of(
                "dev-key-token", "dev-key-fixed", "dev-key-sliding",
                "dev-key-leaky", "dev-key-business",
                "key-acme-dashboard", "key-acme-api",
                "key-beta-dashboard", "key-beta-api",
                "key-enterprise-dashboard", "key-enterprise-api"));

        RateLimiter rateLimiter = mock(RateLimiter.class);
        when(rateLimiter.isRequestAllowed(anyString(), any(), any())).thenReturn(true);
        filter = new ApiKeyFilter(config, rateLimiter);
    }

    @Test
    void validKey_shouldPassThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // To test selective multi-rate limit algorithm,
        // change this line to use one of the valid keys as below
        // dev-key-token, dev-key-token, dev-key-sliding, or dev-key-business
        request.addHeader("X-API-Key", "dev-key-token");
        request.setRequestURI("/api/test123/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        // chain.getRequest() is non-null only if doFilter was called
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void missingKey_shouldReturn401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test123/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull(); // chain was NOT called
        // correct (matches what ApiKeyFilter actually sends):
        assertThat(response.getContentAsString())
                .contains("Request could not be authorised");
    }

    @Test
    void invalidKey_shouldReturn401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "wrong-key");
        request.setRequestURI("/api/test123/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString())
                .contains("Request could not be authorised");
    }

    @Test
    void healthPath_shouldSkipValidation() throws ServletException, IOException {
        // /health has no API key but should still pass through
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void keyMatchingIsCaseInsensitive() throws ServletException, IOException {
        // DEV-KEY-TOKEN is stored lowercase, client sends uppercase should still pass
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "DEV-KEY-TOKEN");
        request.setRequestURI("/api/test123/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void responseBody_containsExpectedFields() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test123/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        String body = response.getContentAsString();
        assertThat(body).contains("\"status\":401");
        assertThat(body).contains("\"error\":\"Unauthorized\"");
        assertThat(body).contains("\"path\":\"/api/test123/notes\"");
    }

    @Test
    void sixthRequest_shouldReturn429() throws ServletException, IOException {
        for (int i = 1; i <= 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-API-Key", "dev-key-token");
            request.setRequestURI("/api/test123/notes");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(chain.getRequest()).isNotNull();
        }

        MockHttpServletRequest request6 = new MockHttpServletRequest();
        // To test selective multi-rate limit algorithm,
        // use one of the valid keys
        request6.addHeader("X-API-Key", "dev-key-token");
        request6.setRequestURI("/api/test123/notes");
        MockHttpServletResponse response6 = new MockHttpServletResponse();
        MockFilterChain chain6 = new MockFilterChain();

        filter.doFilterInternal(request6, response6, chain6);

        assertThat(response6.getStatus()).isEqualTo(429);
        assertThat(chain6.getRequest()).isNull();
        assertThat(response6.getContentAsString())
                .contains("Rate limit exceeded");
    }
}
