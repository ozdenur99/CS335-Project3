package com.CS335_Project3.api_gateway;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class RateLimiter {
    
    /*
       Token bucket rate limiter.

       Concept:
       Each client has a bucket that contains tokens.
       every request they make uses up one token.
       Tokens are added back over time at a fixed refill rate
       If the bucket has no tokens left then the request is rejected
    */

    /*
        This is a helper class to store each clients bucket state
        Tokens = how many tokens client has
        lastRefillTime = when last refilled their bucket
    */
    private static class Bucket{
        double tokens;
        long lastRefillTime;

        Bucket(double tokens, long lastRefillTime){
            this.tokens = tokens;
            this.lastRefillTime = lastRefillTime;
        }
    }


    // HashMaps created:
    // Store one bucket per each client
    private Map<String, Bucket> buckets = new HashMap<>();

    // Set the desired capacity of the bucket
    private final int capacity = 5;

    // Set the refill rate of bucket
    // For example 5 tokens added every one minute
    private final double refillRate = 5.0 / 60000.0;
    

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
        // Create a new bucket for them
        // The bucket starts at full capacity
        if(!buckets.containsKey(clientId)){
            buckets.put(clientId, new Bucket(capacity, now));          
        }

        // Get client bucket
        Bucket bucket = buckets.get(clientId);

        // Get how much time has passed since their last refill
        // and add the tokens back
        long timePassed = now - bucket.lastRefillTime;
        double tokensToAdd = timePassed * refillRate;

        // refill the bucket, but make sure it does not go over the max cap
        bucket.tokens = Math.min(capacity, bucket.tokens + tokensToAdd);

        // update the refill timestamp to now
        bucket.lastRefillTime = now;

        // If the client has at least 1 token.
        // we allow the request and consume 1 token
        if(bucket.tokens >= 1.0){
            bucket.tokens -= 1.0;
            return true;
        }

        // otherwise we reject their request
        return false;
    }


}
