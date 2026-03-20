package com.CS335_Project3.api_gateway.filter;

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
        config.setApiKeys(List.of("key-alpha", "key-beta"));
        filter = new ApiKeyFilter(config);
    }


    @Test
    void validKey_shouldPassThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "key-alpha");
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
        assertThat(chain.getRequest()).isNull();  // chain was NOT called
        assertThat(response.getContentAsString())
            .contains("Missing X-API-Key header");
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
            .contains("Invalid API key");
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
        // key-alpha is stored lowercase, client sends uppercase — should still pass
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "KEY-ALPHA");
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
}
