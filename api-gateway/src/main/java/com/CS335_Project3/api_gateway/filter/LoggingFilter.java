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

//@Component makes it run once only, making all filters share the same log list
@Component
@Order(1)
public class LoggingFilter extends OncePerRequestFilter {

    //we inject RequestLogger and MetricsService so we can record what happened
    private final RequestLogger requestLogger;
    private final MetricsService metricsService;

    public LoggingFilter(RequestLogger requestLogger, MetricsService metricsService) {
        this.requestLogger  = requestLogger;
        this.metricsService = metricsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        //we grab the API key and path from the request before passing it on
        String apiKey = request.getHeader("X-API-Key");
        String path   = request.getRequestURI();

        //if no key was provided, we label it as MISSING
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "MISSING";
        }

        //we pass the request through the rest of the filter chain
        //(running ApiKeyFilter, RateLimiter, AbuseFilter, GatewayController)
        //everything happens in this line, it returns if we know the final status
        chain.doFilter(request, response);

        //we read the final HTTP status code after the chain has finished
        int status = response.getStatus();

        //we see what decision was made and why (based on status code)
        String decision;
        String reason;

        if (status == 401) {
            decision = "BLOCKED";
            reason   = "invalid_or_missing_key";
        } else if (status == 429) {
            decision = "BLOCKED";
            reason   = "rate_limit_exceeded";
        } else if (status == 403) { //waiting for AbuseFilter to implement
            decision = "BLOCKED";
            reason   = "abuse_detected";
        } else {
            decision = "ALLOWED";
            reason   = "ok";
        }

        //returns true if the request was blocked, false if it was allowed
        boolean wasBlocked = decision.equals("BLOCKED");

        //we then record the request in the log and update the metrics counters
        requestLogger.log(apiKey, path, decision, reason);
        metricsService.recordRequest(apiKey, wasBlocked);
    }
}