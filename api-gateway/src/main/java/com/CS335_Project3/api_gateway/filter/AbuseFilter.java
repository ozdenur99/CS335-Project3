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
import com.CS335_Project3.api_gateway.filter.AllowList;
import com.CS335_Project3.api_gateway.filter.RiskScoreService;
import com.CS335_Project3.api_gateway.filter.AbuseEventPublisher;
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
//changed to @Order(2) as it caused requests to be blocked before logging could happen
@Order(2)//runs after LoggingFilter so blocked requests are still captured in logs
public class AbuseFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AbuseFilter.class);
    //added new excluded paths for logging testing
    private static final List<String> EXCLUDED_PATHS = List.of("/health", "/metrics", "/metrics/logs", "/metrics/logs/filter",
            "/metrics/logs/export/json", "/metrics/logs/export/csv",
            "/metrics/suspicious", "/metrics/suspicious/risk",
            "/metrics/latency", "/metrics/risk");

    // private final Spike spike;
    private final Failure failure;
    private final BlockedIps blockedIps;
    private final RiskScoreService riskScoreService;
    private final AbuseEventPublisher eventPublisher;

    public AbuseFilter(Failure failure, BlockedIps blockedIps,AllowList allowList, RiskScoreService riskScoreService, AbuseEventPublisher eventPublisher) {
        // this.spike = spike;
        this.failure = failure;
        this.blockedIps = blockedIps;
        this.allowList        = allowList;
        this.riskScoreService = riskScoreService;
        this.eventPublisher   = eventPublisher;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String ip       = request.getRemoteAddr();
        String apiKey   = request.getHeader("X-API-Key");
        String clientId = (apiKey != null && !apiKey.isBlank()) ? apiKey : ip;

        // Step 1: Skip excluded paths
        if (EXCLUDED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        // Step 2: Skip allowlisted IPs — trusted sources always get through
        if (allowList.isAllowed(ip)) {
            log.info("Allowlisted IP {} — bypassing abuse checks", ip);
            filterChain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        String apiKey = request.getHeader("X-API-Key");
        String clientId = (apiKey != null && !apiKey.isBlank()) ? apiKey : ip;

        // Step 3: Check Redis blocklist
        if (!clientId.equals("dev-key-dynamic") && blockedIps.isBlocked(clientId)) {
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

        // Step 4: Resolve risk score before forwarding
        String riskLevel = riskScoreService.getRiskLevelString(clientId);
        int riskPercent  = riskScoreService.getRiskPercentage(clientId);

        // Step 5: Forward to backend with context headers (T7)
        // These headers tell the backend who is sending and their risk level
        // so the backend can make its own decisions if needed
        request.setAttribute("X-Client-Risk", riskLevel);
        request.setAttribute("X-Client-IP", ip);
        response.setHeader("X-Client-Risk", riskLevel);

        // Wrap request to inject custom headers before forwarding
        HeaderForwardingRequestWrapper wrappedRequest =
                new HeaderForwardingRequestWrapper(request, ip, riskLevel, riskPercent);

        filterChain.doFilter(request, response);

        // Step 6: Failure tracking (post-response)
        int status = response.getStatus();
        if (status == 403 || status == 429 || status == 401) {
            if (failure.recordAndCheck(clientId)) {
                AbuseEvent event = new AbuseEvent(
                        AbuseEvent.Type.REPEATED_FAILURE, clientId, ip,
                        String.format("Repeated failures (last status: %d)", status));
                log.warn(event.toString());
                blockedIps.block(clientId);
                eventPublisher.publishBan(clientId); // notify other gateway instantly
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
