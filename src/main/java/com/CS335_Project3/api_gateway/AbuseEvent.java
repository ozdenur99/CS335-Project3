package com.CS335_Project3.api_gateway;

import java.time.Instant;

/**
 * Represents a detected abuse event.
 * Contains everything the logger needs:
 * type (what kind of abuse was detected), clientId (the API key (or IP if no key present)), ip, timestamp etc.
 */
public class AbuseEvent {

    public enum Type {
        SPIKE,            // Request burst exceeded threshold → 429
        REPEATED_FAILURE, // Too many 429/403 responses → block → 403
        BLOCKED_IP        // Request from already-blocked IP → 403
    }

    private final Type type;
    private final String clientId;
    private final String ip;
    private final Instant timestamp;
    private final String detail;

    public AbuseEvent(Type type, String clientId, String ip, String detail) {
        this.type      = type;
        this.clientId  = clientId;
        this.ip        = ip;
        this.timestamp = Instant.now();
        this.detail    = detail;
    }

    public Type getType()         { return type; }
    public String getClientId()   { return clientId; }
    public String getIp()         { return ip; }
    public Instant getTimestamp() { return timestamp; }
    public String getDetail()     { return detail; }

    @Override
    public String toString() {
        return String.format("[AbuseEvent] type=%s clientId=%s ip=%s at=%s detail=%s",
                type, clientId, ip, timestamp, detail);
    }
}