package com.CS335_Project3.api_gateway.filter;

import com.CS335_Project3.api_gateway.logging.RequestLogger;
import com.CS335_Project3.api_gateway.metrics.MetricsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import com.CS335_Project3.api_gateway.RateLimiter;
import com.CS335_Project3.api_gateway.metrics.BotDetector;
import org.springframework.web.util.ContentCachingResponseWrapper;

//@Component makes it run once only, making all filters share the same log list
//@Order(1) ensures LoggingFilter runs first and wraps the entire chain
//so blocked requests which were not getting logged can now get logged
@Component
@Order(1)
public class LoggingFilter extends OncePerRequestFilter {

    //we inject RequestLogger, MetricsService, RateLimiter and BotDetector so we can record, measure and detect on every request
    private final RequestLogger requestLogger;
    private final MetricsService metricsService;
    private final RateLimiter rateLimiter;
    private final BotDetector botDetector;

    public LoggingFilter(RequestLogger requestLogger, MetricsService metricsService, RateLimiter rateLimiter, BotDetector botDetector) {
        this.requestLogger  = requestLogger;
        this.metricsService = metricsService;
        this.rateLimiter    = rateLimiter;
        this.botDetector    = botDetector;
    }

    //paths excluded from logging so browser generated requests dont pollute the metrics data
    private static final java.util.List<String> EXCLUDED_PATHS =
            java.util.List.of("/health", "/favicon.ico");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        //skips logging for internal/static paths
        String uri = request.getRequestURI();
        if (isExcluded(uri)) {
            chain.doFilter(request, response);
            return;
        }

        //wrap the response so we can always read the status after the chain finishes
        ContentCachingResponseWrapper wrappedResponse =
                new ContentCachingResponseWrapper(response);

        //grab request info
        String apiKey = request.getHeader("X-API-Key");
        String path   = uri;
        long startNs  = System.nanoTime();
        String rawTenant = request.getHeader("X-Tenant-Id");
        String rawApp = request.getHeader("X-App-Id");
        String tenantId = (rawTenant == null || rawTenant.isBlank()) ? "default" : rawTenant.toLowerCase();
        String appId = (rawApp == null || rawApp.isBlank()) ? "default" : rawApp.toLowerCase();

        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "MISSING";
        }

        //gets the client IP and which rate limiting algorithm they are assigned to
        String ip        = request.getRemoteAddr();
        String algorithm = rateLimiter.getAlgorithm(apiKey.toLowerCase(), tenantId, appId);

        //records IP for bot detection
        botDetector.record(ip);
        if (botDetector.isSuspicious(ip)) {
            wrappedResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            requestLogger.log(apiKey, ip, path, "FLAGGED", "suspected_bot", algorithm);
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            metricsService.recordRequest(apiKey, ip, path, HttpServletResponse.SC_FORBIDDEN, latencyMs, algorithm, "BLOCKED", "suspected_bot", tenantId, appId);
            wrappedResponse.copyBodyToResponse();
            return;
        }

        //pass the wrapped response through the chain
        chain.doFilter(request, wrappedResponse);

        //now we can always read the real status code
        int status = wrappedResponse.getStatus();
        long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;

        String decision;
        String reason;

        if (status == 401) {
            decision = "BLOCKED";
            reason   = "invalid_or_missing_key";
        } else if (status == 429) {
            decision = "BLOCKED";
            reason   = "rate_limit_exceeded";
        } else if (status == 403) {
            decision = "BLOCKED";
            reason   = "abuse_detected";
        } else {
            decision = "ALLOWED";
            reason   = "ok";
        }

        //returns true if the request was blocked for any reason, false if it was allowed through
        boolean wasBlocked = decision.equals("BLOCKED");

        //records the full request details in the log and update the metrics counters
        requestLogger.log(apiKey, ip, path, decision, reason, algorithm);
        metricsService.recordRequest(apiKey, ip, path, status, latencyMs, algorithm, decision, reason, tenantId, appId);

        //copies the response body back so the client still receives it
        //(ContentCachingResponseWrapper holds it in memory)
        wrappedResponse.copyBodyToResponse();
    }

    private boolean isExcluded(String path) {
        return EXCLUDED_PATHS.contains(path)
                || path.startsWith("/metrics")
                || path.startsWith("/dashboard")
                || path.startsWith("/config");
    }
}
