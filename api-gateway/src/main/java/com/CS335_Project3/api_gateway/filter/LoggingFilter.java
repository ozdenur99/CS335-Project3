package com.CS335_Project3.api_gateway.filter;

import com.CS335_Project3.api_gateway.RateLimiter;
import com.CS335_Project3.api_gateway.logging.LogEntry;
import com.CS335_Project3.api_gateway.logging.LogForwarder;
import com.CS335_Project3.api_gateway.logging.RequestLogger;
import com.CS335_Project3.api_gateway.metrics.BotDetector;
import com.CS335_Project3.api_gateway.metrics.MetricsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import java.util.UUID;

//@Component makes Spring create one single instance shared across the whole app
//@Order(1) ensures LoggingFilter runs first and wraps the entire chain
//this way it can always read the final status code after all other filters finish
//if this was not Order(1) blocked requests would never get logged
@Component
@Order(1)
public class LoggingFilter extends OncePerRequestFilter {

    // these paths are excluded from logging so browser generated requests
    // like favicon.ico and metrics page loads dont pollute the metrics data
    private static final List<String> EXCLUDED_PATHS = List.of("/health", "/metrics", "/metrics/logs", "/favicon.ico",
            // add the paths for checking metrics to exclude them from logging or risk score
            // calculation,
            // otherwise every time team metrics check gets logged and counted
            "/metrics/logs/filter", "/metrics/logs/export/json", "/metrics/logs/export/csv",
            "/metrics/suspicious", "/metrics/suspicious/risk",
            "/metrics/latency", "/metrics/risk", "/metrics/timeseries", "/metrics/clients", "/metrics/gateway");

    // gateway-1 is just the fallback default used when running locally
    // without Docker (where GATEWAY_ID env var isn't set).
    @Value("${GATEWAY_ID:gateway-1}")
    private String gatewayId;

    // we inject all 5 dependencies so we can record, measure, detect and forward on
    // every request
    private final RequestLogger requestLogger;
    private final MetricsService metricsService;
    private final RateLimiter rateLimiter; // used to look up which algorithm the client uses
    private final BotDetector botDetector; // used to flag suspicious IPs
    private final LogForwarder logForwarder; // used to forward logs to the backend in real time

    public LoggingFilter(RequestLogger requestLogger, MetricsService metricsService,
            RateLimiter rateLimiter, BotDetector botDetector,
            LogForwarder logForwarder) {
        this.requestLogger = requestLogger;
        this.metricsService = metricsService;
        this.rateLimiter = rateLimiter;
        this.botDetector = botDetector;
        this.logForwarder = logForwarder;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain)
            throws ServletException, IOException {

        // skip logging for internal/static paths so they dont show up in metrics
        if (EXCLUDED_PATHS.contains(request.getRequestURI()) || request.getRequestURI().startsWith("/admin")) {
            chain.doFilter(request, response);
            return;
        }

        // record the start time so we can calculate how long the request took
        long startTime = System.currentTimeMillis();

        // wrap the response so we can always read the status after the chain finishes
        // without this Spring sometimes commits the response before we can read it
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        // generate a unique request ID for tracing this request across all logs and
        // metrics
        String requestId = UUID.randomUUID().toString();
        wrappedResponse.setHeader("X-Request-ID", requestId);

        // grab all the request info we need before passing it on
        String apiKey = request.getHeader("X-API-Key");
        String path = request.getRequestURI();

        // if no key was provided label it as MISSING so it still appears in logs
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "MISSING";
        }

        // get the client IP and which rate limiting algorithm they are assigned to
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = (forwarded != null && !forwarded.isBlank()) ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        String rawTenant = request.getHeader("X-Tenant-Id");
        String rawApp = request.getHeader("X-App-Id");
        String tenantId = (rawTenant == null || rawTenant.isBlank()) ? null : rawTenant.toLowerCase();
        String appId = (rawApp == null || rawApp.isBlank()) ? null : rawApp.toLowerCase();
        String algorithm = rateLimiter.getResolvedAlgorithm(apiKey.toLowerCase(), tenantId, appId);
        // String algorithm = rateLimiter.getAlgorithm(apiKey.toLowerCase());

        // record the IP in BotDetector and check if it has exceeded the threshold
        // if flagged we log it immediately and return early without forwarding the
        // request
        botDetector.record(ip);
        if (botDetector.isSuspicious(ip)) {
            long latencyMs = System.currentTimeMillis() - startTime;
            LogEntry flaggedEntry = new LogEntry(apiKey, ip, path, "FLAGGED", "suspected_bot", algorithm, latencyMs,
                    gatewayId, requestId);
            requestLogger.log(apiKey, ip, path, "FLAGGED", "suspected_bot", algorithm, latencyMs, gatewayId, requestId);
            metricsService.recordRequest(apiKey, true, latencyMs, 0, gatewayId);
            logForwarder.forward(flaggedEntry);
            wrappedResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            wrappedResponse.setContentType("application/json");
            wrappedResponse.setCharacterEncoding("UTF-8");
            wrappedResponse.getWriter().write(String.format(
                    "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Suspicious bot activity detected.\",\"path\":\"%s\"}",
                    path));
            wrappedResponse.copyBodyToResponse();
            return;
        }

        // passes the wrapped response through the rest of the filter chain
        // this runs AbuseFilter, ApiKeyFilter, RateLimiter and GatewayController
        // everything happens inside this line after it returns we know the final
        // outcome
        chain.doFilter(request, wrappedResponse);

        // calculate how long the full request took in milliseconds
        long latencyMs = System.currentTimeMillis() - startTime;

        // read the final HTTP status code from the wrapped response
        // this is now always reliable because the wrapper held it in memory
        int status = wrappedResponse.getStatus();

        // map the status code to a human readable decision and reason
        String decision;
        String reason;

        if (status == 401) {
            decision = "BLOCKED";
            reason = "invalid_or_missing_key";
        } else if (status == 429) {
            decision = "BLOCKED";
            reason = "rate_limit_exceeded";
        } else if (status == 403) {
            decision = "BLOCKED";
            reason = "abuse_detected";
        } else {
            decision = "ALLOWED";
            reason = "ok";
        }

        // true if the request was blocked for any reason, false if it was allowed
        // through
        boolean wasBlocked = decision.equals("BLOCKED");

        // record the full request details in the log and update the metrics counters
        requestLogger.log(apiKey, ip, path, decision, reason, algorithm, latencyMs, gatewayId, requestId);
        // pass latency and status code to MetricsService so it can track percentiles
        // and status breakdowns
        metricsService.recordRequest(apiKey, wasBlocked, latencyMs, status, gatewayId);

        // forward the log entry to the backend in real time
        logForwarder
                .forward(new LogEntry(apiKey, ip, path, decision, reason, algorithm, latencyMs, gatewayId, requestId));

        // copy the response body back so the client still receives it
        wrappedResponse.copyBodyToResponse();
    }
}