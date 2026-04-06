package com.CS335_Project3.api_gateway.filter;

import com.CS335_Project3.api_gateway.RateLimiter;
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
@Order(3)
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log =
        LoggerFactory.getLogger(ApiKeyFilter.class);

    private static final String API_KEY_HEADER = "X-API-Key";

    // These paths bypass API key validation.
    // /health — lets teammates check the Gateway is running without a key.
    // /metrics — lets metrics endpoints work without a key.
    private static final List<String> EXCLUDED_PATHS =
        List.of("/health", "/metrics", "/metrics/logs");

    private final ApiKeyConfig apiKeyConfig;
    private final RateLimiter rateLimiter;

    public ApiKeyFilter(ApiKeyConfig apiKeyConfig, RateLimiter rateLimiter) {
        this.apiKeyConfig = apiKeyConfig;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip validation for excluded paths.
        if (EXCLUDED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("BLOCKED reason=missing_key path={}", path);
            sendError(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized", "Request could not be authorised.");
            return;
        }

        if (!apiKeyConfig.isValidKey(apiKey)) {
            log.warn("BLOCKED reason=invalid_key key={} path={}", apiKey, path);
            sendError(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized", "Request could not be authorised.");
            return;
        }

        // Rate limiter logic
        if (!rateLimiter.isRequestAllowed(apiKey.toLowerCase())) {
            log.warn("BLOCKED reason=rate_limit_exceeded key={} path={}", apiKey, path);
            sendError(response, request, 429,
                "Too Many Requests", "Request could not be processed at this time.");
            return;
        }

        // Key is valid — pass the request to the next filter in the chain.
        log.info("ALLOWED key={} path={}", apiKey, path);
        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response,
                           HttpServletRequest request,
                           int status,
                           String error,
                           String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
            "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
            status,
            error,
            message,
            request.getRequestURI()
        ));
    }
}