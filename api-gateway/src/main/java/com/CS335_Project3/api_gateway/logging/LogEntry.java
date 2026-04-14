package com.CS335_Project3.api_gateway.logging;

import java.time.LocalDateTime;

public class LogEntry {

    //these are the fields that get recorded for every single request
    //they are final because a log entry should never be changed after creation
    private final LocalDateTime timestamp; // exact time of when the request came in
    private final String apiKey;           // the key the client used
    private final String ip;               // the IP address of the client
    private final String path;             // the URL the client requested
    private final String decision;         // if the gateway either allows or blocks the request
    private final String reason;           // why the request was allowed or blocked
    private final String algorithm;        // the rate limiting algorithm used for the request
    private final long latencyMs;          // how long the request took in milliseconds

    public LogEntry(String apiKey, String ip, String path, String decision,
                    String reason, String algorithm, long latencyMs) {
        this.timestamp = LocalDateTime.now();
        this.apiKey    = apiKey;
        this.ip        = ip;
        this.path      = path;
        this.decision  = decision;
        this.reason    = reason;
        this.algorithm = algorithm;
        this.latencyMs = latencyMs;
    }

    //Spring needs these getters to convert this object to JSON
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getApiKey()           { return apiKey; }
    public String getIp()               { return ip; }
    public String getPath()             { return path; }
    public String getDecision()         { return decision; }
    public String getReason()           { return reason; }
    public String getAlgorithm()        { return algorithm; }
    public long getLatencyMs()          { return latencyMs; }
}