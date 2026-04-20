package com.CS335_Project3.api_gateway.config;

import com.CS335_Project3.api_gateway.RateLimiter;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

// Listens on the "config-reload" Redis Pub/Sub channel.
// When ConfigController publishes an update, all gateway instances
// receive this message and reload their in-memory config from Redis.
@Component
public class ConfigReloadListener implements MessageListener {

    private final RateLimiter rateLimiter;

    public ConfigReloadListener(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        // Triggered on every gateway that has this listener registered.
        // Reads the latest values from Redis Hash and applies them in-memory.
        rateLimiter.reloadConfig();
    }
}
