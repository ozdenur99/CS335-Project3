package com.CS335_Project3.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Hierarchical rate limit configuration for each tenant.
 * This class maps "rate-limit" properties from application.properties, 
 * supporting global, per-tenant, and per-app limits.
 * */
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class TenantRateLimitConfig {

    // Global default limits
    private int defaultLimit = 5;  
    private String defaultAlgorithm = "token";

    /** * Map of Tenant-specific policies. 
     * Key: tenantId (e.g., "tenant-acme") 
     * Initializing a HashMap to empty, so map.get(x) never throws 
     * NullPointerException(NPE) when a tenant isn't configured.
     */
    private Map<String, TenantPolicy> tenants = new HashMap<>();

    // Standard getters and setters for Spring Bingding
    public int getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public String getDefaultAlgorithm() {
        return defaultAlgorithm;
    }

    public void setDefaultAlgorithm(String defaultAlgorithm) {
        this.defaultAlgorithm = defaultAlgorithm;
    }

    public Map<String, TenantPolicy> getTenants() {
        return tenants;
    }

    public void setTenants(Map<String, TenantPolicy> tenants) {
        this.tenants = tenants;
    }

    /**
     * Policy settings for a specific Tenant.
     */
    public static class TenantPolicy {
        private int limit = 5;
        // private String algorithm = "token";
        private boolean enabled = true;
        //inherited algorithm: if null, tenant inherits global default; 
        // if app also null, app inherits tenant/global default.
        private String algorithm = null;  

        /** * Map of Application-specific policies within this tenant. 
         * Key: appId (e.g., "dashboard" or "api")  
         * Initializing a HashMap to empty, so map.get(x) never throws 
         * NullPointerException(NPE) when a app isn't configured.
         */
        private Map<String, AppPolicy> apps = new HashMap<>();

        // Getters and Setters
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }

        public String getAlgorithm() { return algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Map<String, AppPolicy> getApps() { return apps; }
        public void setApps(Map<String, AppPolicy> apps) { this.apps = apps; }
    }

    /**
     * Policy settings for a specific App under a Tenant.
     */
    public static class AppPolicy {
        private int limit = 5;
        private boolean enabled = true; 
        // If null, app inherits tenant's algorithm; if tenant's is also null, inherits global default.
        private String algorithm = null;  
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        // Getters and Setters for algorithm
        public String getAlgorithm() { return algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    }

}