package com.CS335_Project3.api_gateway.logging;

import java.time.LocalDateTime; //used to get the exact time of the request

public class LogEntry {

    //we establish the 5 fields to be displayed as finals since they will not be changed
    private final LocalDateTime timestamp; //exact time of when the request came in
    private final String apiKey;           //the key the client used
    private final String path;             //the URL the client requested
    private final String decision;         //if the gateway either allows the request or blocks it
    private final String reason;           //why the request was allowed or blocked (403,200...)


    //we set the fields with a constructor
    public LogEntry(String apiKey, String path, String decision, String reason) {
        this.timestamp = LocalDateTime.now();
        this.apiKey = apiKey;
        this.path = path;
        this.decision = decision;
        this.reason = reason;
    }

    //set get functions for Spring to convert when MetricsController (in metrics folder)
    //returns the logs as response
    public LocalDateTime getTimeStamp(){
        return timestamp;
    }
    public String getApiKey(){
        return apiKey;
    }
    public String getPath(){
        return path;
    }
    public String getDecision(){
        return decision;
    }
    public String getReason(){
        return reason;
    }
}
