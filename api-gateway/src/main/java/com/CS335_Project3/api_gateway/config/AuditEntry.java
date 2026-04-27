package com.CS335_Project3.api_gateway.config;

public class AuditEntry {

    private String timestamp;
    private String tenant;
    private String app;
    private String oldAlgorithm;
    private String newAlgorithm;
    private Integer oldLimit;
    private Integer newLimit;
    private String gatewayId;

    public AuditEntry() {
    }

    public AuditEntry(String timestamp, String tenant, String app,
            String oldAlgorithm, String newAlgorithm,
            Integer oldLimit, Integer newLimit,
            String gatewayId) {
        this.timestamp = timestamp;
        this.tenant = tenant;
        this.app = app;
        this.oldAlgorithm = oldAlgorithm;
        this.newAlgorithm = newAlgorithm;
        this.oldLimit = oldLimit;
        this.newLimit = newLimit;
        this.gatewayId = gatewayId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getTenant() {
        return tenant;
    }

    public String getApp() {
        return app;
    }

    public String getOldAlgorithm() {
        return oldAlgorithm;
    }

    public String getNewAlgorithm() {
        return newAlgorithm;
    }

    public Integer getOldLimit() {
        return oldLimit;
    }

    public Integer getNewLimit() {
        return newLimit;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public void setOldAlgorithm(String oldAlgorithm) {
        this.oldAlgorithm = oldAlgorithm;
    }

    public void setNewAlgorithm(String newAlgorithm) {
        this.newAlgorithm = newAlgorithm;
    }

    public void setOldLimit(Integer oldLimit) {
        this.oldLimit = oldLimit;
    }

    public void setNewLimit(Integer newLimit) {
        this.newLimit = newLimit;
    }

    public void setGatewayId(String gatewayId) {
        this.gatewayId = gatewayId;
    }
}
