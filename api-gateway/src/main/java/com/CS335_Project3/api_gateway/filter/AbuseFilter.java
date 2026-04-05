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
 * WHAT THIS MODULE DOES:
 *   1. Blocks requests from known-bad IPs → 403
 *   2. Tracks repeated 403 failures per client
 *   3. Auto-blocks IPs that keep hitting the blocklist
 *   4. Blocks automatically expire after a cooldown period (default 5 minutes)
 *
 * DETECTION FLOW:
 *   1. Skip /health and /metrics
 *   2. Blocked IP check-- 403, stop
 *   3. Spike detection -- 429, auto-block IP, stop
 *   4. Forward to backend
 *   5. Failure tracking ,if response was 429 or 403, record it.
 *                           If threshold exceeded auto-block IP
 */
@Component
@Order(3)
public class AbuseFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AbuseFilter.class);
    private static final List<String> EXCLUDED_PATHS =
            List.of("/health", "/metrics", "/metrics/logs");

    private final Failure failure;
    private final BlockedIps blockedIps;

    public AbuseFilter(Failure failure,
                       BlockedIps blockedIps) {
        this.failure = failure;
        this.blockedIps    = blockedIps;
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

        String ip       = request.getRemoteAddr();
        String apiKey   = request.getHeader("X-API-Key");
        String clientId = (apiKey != null && !apiKey.isBlank()) ? apiKey : ip;

        // Step 2: Blocked IP check
        // isBlocked() automatically unblocks expired blocks
        if (blockedIps.isBlocked(ip)) {
            AbuseEvent event = new AbuseEvent(
                    AbuseEvent.Type.BLOCKED_IP, clientId, ip,
                    "Request from blocked IP");
            log.warn(event.toString());
            sendError(response, request, HttpServletResponse.SC_FORBIDDEN,
                    "Forbidden", "Your IP has been blocked due to abuse.");
            return;
        }

        // Step 3: Forward to backend
        filterChain.doFilter(request, response);

        // Step 4: Failure tracking (post-response)
        int status = response.getStatus();
        if (status == HttpServletResponse.SC_FORBIDDEN) {
            if (failure .recordAndCheck(clientId)) {
                AbuseEvent event = new AbuseEvent(
                        AbuseEvent.Type.REPEATED_FAILURE, clientId, ip,
                        String.format("Repeated 403 failures (last status: %d)", status));
                log.warn(event.toString());
                blockedIps.block(ip);
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
