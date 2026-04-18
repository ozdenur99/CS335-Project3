package com.CS335_Project3.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
public class RedisConfig {
    // 1. Define the ConnectionFactory as a proper Bean
    // This allows Spring to manage its lifecycle and inject it elsewhere
    @Bean
    public RedisConnectionFactory redisConnectionFactory(
            @Value("${REDIS_HOST:redis}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort) {
        return new LettuceConnectionFactory(redisHost, redisPort);
    }

    // 2. Inject the factory bean defined above
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Use plain strings for both keys and values — no Java serialization
        // String serializer keeps keys exactly as you write them —
        // rl:token:tenant-acme/dashboard
        // This keeps Redis keys human-readable
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        return template;
    }

    // Registers the Pub/Sub listener so this gateway reacts to config-reload messages.
    // When any gateway POSTs to /admin/config, all gateways reload instantly.
    @Bean(destroyMethod = "destroy")
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory,
            ConfigReloadListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(listener, new ChannelTopic("config-reload"));
        return container;
    }
}
