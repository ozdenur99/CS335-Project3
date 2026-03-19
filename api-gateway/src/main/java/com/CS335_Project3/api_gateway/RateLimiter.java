package com.CS335_Project3.api_gateway;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class RateLimiter {
    
    /*
        Simple fixed-window rate limiter. We can expand and change to for e.g token bucket later

        Basic Concept:

        Each client gets fixed time window (in this case i chose 1 minute)
        During that window we count how many requests they make

        If thes number of requests they make goes over the limit, which I chose as 5,
        then their request should be rejected

        Once the window has expired, the rquesst count resets and the client can send requests again
    */

    // HashMaps created:
    // requestCounts = Stores how many requests a client has made
    // windowStart = Stores the time when the rate limit window started for a client
    private Map<String, Integer> requestCounts = new HashMap<>();
    private Map<String, Long> windowStart = new HashMap<>();

    // Limit is max amount of reqs
    // Window is timeframe in milliseconds (1 minute)
    private final int limit = 5;
    private final long window = 60000;

    /* 
        This method will be called by our API key flter later
        It checks whether a request from a given client is allowed or not
        I used syncronized to prevent issues if multiple requests arrive a client at the same time
        This should stop requests incorrectly being allowed
    */
    public synchronized boolean isRequestAllowed(String clientId){

        // Get the current time
        long now = System.currentTimeMillis();

        // If this is the users first rquest,
        // we initialise their rate limit window and request counter
        if(!windowStart.containsKey(clientId)){
            windowStart.put(clientId, now);             // start a new window
            requestCounts.put(clientId, 0);      // initialise their counter
        }

        // Get the time when the users current window has started
        long startTime = windowStart.get(clientId);

        // This is basically the core of the fixed window rate limiter
        // We check if the clients current window has expired
        // If this is the case we reset their window and their request count
        if(now - startTime > window){

            // Reset the window start time
            windowStart.put(clientId, now);

            // Reset the count
            requestCounts.put(clientId, 0);
        }

        // Get the curent request count for the client
        int count = requestCounts.get(clientId);

        // Increase the count
        count++;

        // Update with the new count
        requestCounts.put(clientId, count);

        // return the allowed request, if the client is under the limit
        return count <= limit;
    }


}
