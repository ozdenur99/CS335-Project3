package com.CS335_Project3.api_gateway.filter;

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

/**
 * WHAT THIS RETURNS:
 *   429 spike detected
 *   403 blocked IP
 *
 * DETECTION FLOW:
 *   1. Skip /health and /metrics
 *   2. Blocked IP check -- 403, stop
 *   3. Spike detection -- 429, auto-block IP, stop
 *   4. Forward to backend
 *   5. Failure tracking, if response was 429 or 403, record it.
 *      If threshold exceeded, auto-block IP
 */
@Component
@Order(1)
public class AbuseFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AbuseFilter.class);
    private static final List<String> EXCLUDED_PATHS = List.of("/health", "/metrics");

    // private final Spike spike;
    private final Failure failure;
    private final BlockedIps blockedIps;

    public AbuseFilter(Failure failure,
                       BlockedIps blockedIps) {
        // this.spike = spike;
        this.failure = failure;
        this.blockedIps = blockedIps;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Step 1: Skip excluded paths
        if (EXCLUDED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        String apiKey = request.getHeader("X-API-Key");
        String clientId = (apiKey != null && !apiKey.isBlank()) ? apiKey : ip;

        if (blockedIps.isBlocked(clientId)) {
            log.warn("Blocked client check triggered for {}", clientId);
            AbuseEvent event = new AbuseEvent(
                    AbuseEvent.Type.BLOCKED_IP, clientId, ip, "Request from blocked client");
            log.warn(event.toString());
            sendError(response, request, 403,
                    "Forbidden", "Access denied.");
            return;
        }

        // Step 3: Spike detection
        // if (spike.recordAndCheck(clientId)) {
        //     AbuseEvent event = new AbuseEvent(
        //             AbuseEvent.Type.SPIKE, clientId, ip, "Request spike threshold exceeded");
        //     log.warn(event.toString());

        //     boolean shouldBlock = failure.recordAndCheck(clientId);
        //     log.warn("Spike recorded for clientId={}, ip={}, shouldBlock={}", clientId, ip, shouldBlock);

        //     if (shouldBlock) {
        //         AbuseEvent blockEvent = new AbuseEvent(
        //                 AbuseEvent.Type.REPEATED_FAILURE, clientId, ip,
        //                 "Repeated spike/failure threshold exceeded");
        //         log.warn(blockEvent.toString());
        //         blockedIps.block(clientId);
        //         log.warn("Client {} added to blocklist", ip);
        //     }

        //     sendError(response, request, 429,
        //             "Too Many Requests", "Request could not be processed at this time.");
        //     return;
        // }

        // Step 4: Forward to backend
        filterChain.doFilter(request, response);

        // Step 5: Failure tracking (post-response)
        int status = response.getStatus();
        if (status == 403) {
            if (failure.recordAndCheck(clientId)) {
                AbuseEvent event = new AbuseEvent(
                        AbuseEvent.Type.REPEATED_FAILURE, clientId, ip,
                        String.format("Repeated failures (last status: %d)", status));
                log.warn(event.toString());
                blockedIps.block(clientId);
            }
        }
    }

    private void sendError(HttpServletResponse response,
                           HttpServletRequest request,
                           int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
                "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                status, error, message, request.getRequestURI()
        ));
    }
}
