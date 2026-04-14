package com.CS335_Project3.api_gateway.logging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

//@Component makes Spring create one single instance shared across the whole app
//sends each log entry to the backend in real time after every request
@Component
public class LogForwarder {

    private final RestTemplate restTemplate;

    //reads the backend URL from application.properties
    //falls back to localhost:8081 if not set
    @Value("${backend.url:http://localhost:8081/api/}")
    private String backendUrl;

    public LogForwarder(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    //posts the log entry to the backend as JSON (called by LoggingFilter after every request)
    //uses try/catch so a backend failure never crashes the gateway
    public void forward(LogEntry entry) {
        try {
            String url = backendUrl + "logs";
            restTemplate.postForObject(url, entry, String.class);
        } catch (Exception e) {
            //ignores if backend is unreachable
        }
    }
}