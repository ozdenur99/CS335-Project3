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
// previously was Order(1) which caused 401 and 429 blocks when testing logging
@Order(3) // runs after LoggingFilter and AbuseFilter
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    private static final String API_KEY_HEADER = "X-API-Key";

    // These paths bypass API key validation.
    // /health — lets teammates check the Gateway is running without a key.
    // "/metrics" : lets metrics endpoints work without a key (allows display in
    // http://localhost:8080/metrics).
    // "/metrics/logs" : lets logs endpoints (/export, /suspicious, /filter...) work
    // without a key (allows display in http://localhost:8080/metrics/logs...).
    private static final List<String> EXCLUDED_PATHS = List.of("/health", "/metrics", "/metrics/logs",
            "/metrics/logs/filter",
            "/metrics/logs/export/json", "/metrics/logs/export/csv",
            "/metrics/suspicious", "/metrics/suspicious/risk",
            "/metrics/latency", "/metrics/risk", "/favicon.ico",
            "/metrics/timeseries", "/metrics/clients", "/metrics/gateway");

    private static final String ADMIN_KEY_HEADER = "X-Admin-Key";
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

        // Admin paths require a separate admin key
        if (path.startsWith("/admin")) {
            String adminKey = request.getHeader(ADMIN_KEY_HEADER);
            if (!apiKeyConfig.isAdminKey(adminKey)) {
                log.warn("BLOCKED reason=missing_admin_key path={}", path);
                sendError(response, request, HttpServletResponse.SC_FORBIDDEN,
                        "Forbidden", "Admin access requires X-Admin-Key header.");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

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
        // Only use tenant/app if they were actually provided
        String rawTenant = request.getHeader("X-Tenant-Id");
        String rawApp = request.getHeader("X-App-Id");

        String tenantId = (rawTenant == null || rawTenant.isBlank()) ? null : rawTenant.toLowerCase();
        String appId = (rawApp == null || rawApp.isBlank()) ? null : rawApp.toLowerCase();

        if (!rateLimiter.isRequestAllowed(apiKey.toLowerCase(), tenantId, appId)) {
            log.warn("BLOCKED reason=rate_limit_exceeded key={} tenant={} app={} path={}",
                    apiKey, tenantId, appId, path);
            response.setHeader("X-RateLimit-Limit",
                    String.valueOf(rateLimiter.getResolvedLimit(apiKey.toLowerCase(), tenantId, appId)));
            response.setHeader("X-RateLimit-Algorithm",
                    rateLimiter.getResolvedAlgorithm(apiKey.toLowerCase(), tenantId, appId));
            sendError(response, request, 429,
                    "Too Many Requests", "Request could not be processed at this time.");
            return;
        }

        // Key is valid — pass the request to the next filter in the chain.
        log.info("ALLOWED key={} tenant={} app={} path={}", apiKey, tenantId, appId, path);
        response.setHeader("X-RateLimit-Limit",
                String.valueOf(rateLimiter.getResolvedLimit(apiKey.toLowerCase(), tenantId, appId)));
        response.setHeader("X-RateLimit-Algorithm",
                rateLimiter.getResolvedAlgorithm(apiKey.toLowerCase(), tenantId, appId));
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
        // For 429 Too Many Requests, include a Retry-After header
        // to indicate when the client can try again.
        if (status == 429) {
            response.setHeader("Retry-After", "10");
        }
        response.getWriter().write(String.format(
                "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                status,
                error,
                message,
                request.getRequestURI()));
    }
}