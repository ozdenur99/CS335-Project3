package com.CS335_Project3.api_gateway.filter;

import com.CS335_Project3.api_gateway.config.ApiKeyConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;


@Component
@Order(1)
public class ApiKeyFilter extends OncePerRequestFilter {


    private static final Logger log =
        LoggerFactory.getLogger(ApiKeyFilter.class);


    private static final String API_KEY_HEADER = "X-API-Key";


    // These paths bypass API key validation.
    // /health — lets teammates check the Gateway is running without a key.
    // /metrics — lets Mateo's metrics endpoint work without a key.
    private static final List<String> EXCLUDED_PATHS =
        List.of("/health", "/metrics");


    private final ApiKeyConfig apiKeyConfig;


    public ApiKeyFilter(ApiKeyConfig apiKeyConfig) {
        this.apiKeyConfig = apiKeyConfig;
    }


    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {


        String path = request.getRequestURI();


        // Skip validation for excluded paths.
        // Call doFilter and return immediately — do not run the key check.
        if (EXCLUDED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }


        String apiKey = request.getHeader(API_KEY_HEADER);


        if (apiKey == null || apiKey.isBlank()) {
            log.warn("BLOCKED reason=missing_key path={}", path);
            sendError(response, request, "Missing X-API-Key header");
            return;  // do NOT call filterChain.doFilter after sending an error
        }


        if (!apiKeyConfig.isValidKey(apiKey)) {
            log.warn("BLOCKED reason=invalid_key key={} path={}", apiKey, path);
            sendError(response, request, "Invalid API key");
            return;  // do NOT call filterChain.doFilter after sending an error
        }


        // Key is valid — pass the request to the next filter in the chain.
        log.info("ALLOWED key={} path={}", apiKey, path);
        filterChain.doFilter(request, response);
    }


    private void sendError(HttpServletResponse response,
                           HttpServletRequest request,
                           String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
            "{\"status\":401,\"error\":\"Unauthorized\"," +
            "\"message\":\"%s\",\"path\":\"%s\"}",
            message,
            request.getRequestURI()
        ));
    }
}

