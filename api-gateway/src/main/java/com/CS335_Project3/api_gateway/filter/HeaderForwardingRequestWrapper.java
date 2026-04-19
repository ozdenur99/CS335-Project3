package com.CS335_Project3.api_gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Wraps the incoming HttpServletRequest to inject additional headers
 * before the request is forwarded to the backend service (T7).
 *
 * WHY A WRAPPER:
 * HttpServletRequest headers are read-only — you cannot add headers
 * directly. A wrapper that overrides getHeader() and getHeaderNames()
 * is the standard Spring/Java way to inject headers into a request.
 *
 * HEADERS INJECTED:
 *   X-Gateway-Client-IP    — the real IP of the client
 *   X-Gateway-Risk-Level   — NONE/LOW/MEDIUM/HIGH risk label
 *   X-Gateway-Risk-Percent — 0-100 score showing proximity to block
 *
 * The backend can read these headers and use them for its own logic,
 * logging, or to make decisions about how to handle the request.
 */
public class HeaderForwardingRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> extraHeaders = new HashMap<>();

    public HeaderForwardingRequestWrapper(HttpServletRequest request,
                                          String clientIp,
                                          String riskLevel,
                                          int riskPercent) {
        super(request);
        extraHeaders.put("X-Gateway-Client-IP",    clientIp);
        extraHeaders.put("X-Gateway-Risk-Level",   riskLevel);
        extraHeaders.put("X-Gateway-Risk-Percent", String.valueOf(riskPercent));
    }

    @Override
    public String getHeader(String name) {
        // Check injected headers first, then fall through to original
        String extra = extraHeaders.get(name);
        return extra != null ? extra : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        // Merge original header names with injected ones
        java.util.List<String> names = Collections.list(super.getHeaderNames());
        names.addAll(extraHeaders.keySet());
        return Collections.enumeration(names);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String extra = extraHeaders.get(name);
        if (extra != null) {
            return Collections.enumeration(Collections.singletonList(extra));
        }
        return super.getHeaders(name);
    }
}