package com.CS335_Project3.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import com.CS335_Project3.api_gateway.filter.AbuseEventSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import java.time.Duration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;

@Configuration
public class RedisConfig {
    // 1. Define the ConnectionFactory as a proper Bean
    // This allows Spring to manage its lifecycle and inject it elsewhere
    @Bean
    public RedisConnectionFactory redisConnectionFactory(
            @Value("${REDIS_HOST:localhost}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort) {

        // Configure the Redis connection with host and port from environment variables
        // where to connect (host + port)
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration(redisHost, redisPort);

        // with 2s timeout, Redis commands fail fast,the gateway can then
        // return 504 instead of blocking the client.
        // LettuceClientConfiguration allows us to set a command timeout,
        // how to connect (timeout, SSL, etc.)
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2)) // 2s max for any Redis command
                .build();
        return new LettuceConnectionFactory(serverConfig, clientConfig);
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

    // Registers the Pub/Sub listener so this gateway reacts to config-reload
    // messages.
    // When any gateway POSTs to /admin/config, all gateways reload instantly.
    @Bean(destroyMethod = "destroy")
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory,
            ConfigReloadListener listener,
            AbuseEventSubscriber abuseEventSubscriber,
            AbuseDetectionConfig abuseDetectionConfig) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer() {
            private static final org.slf4j.Logger log = org.slf4j.LoggerFactory
                    .getLogger(RedisMessageListenerContainer.class);

            @Override
            public void start() {
                try {
                    super.start();
                } catch (Exception e) {
                    log.warn("Redis pub/sub unavailable at startup — listener inactive: {}", e.getMessage());
                }
            }
        };
        container.setConnectionFactory(factory);
        container.addMessageListener(listener, new ChannelTopic("config-reload"));
        container.addMessageListener(abuseEventSubscriber,
                new ChannelTopic(abuseDetectionConfig.getRedis().getChannel()));
        return container;
    } 
}
