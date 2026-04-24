package com.CS335_Project3.api_gateway;

import com.CS335_Project3.api_gateway.config.AbuseDetectionConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

// enables @Scheduled methods: MetricsExporter snapshots + RateLimiter Redis health check
@SpringBootApplication
@EnableScheduling
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        // serializes LocalDateTime as an array in the RestTemplate to handle Java 8
        // date/time types in our logging,
        // so forwarded logs show readable timestamps like "2026-04-17T10:30:00"
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(mapper);
        // RestTemplate restTemplate = new RestTemplate();

        //replace new RestTemplate() with a factory that has timeouts.
        //this prevents the gateway from hanging indefinitely if the backend is down or slow to respond.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); // 2s to connect to backend
        factory.setReadTimeout(5000); // 5s to wait for backend response
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getMessageConverters().removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
        restTemplate.getMessageConverters().add(0, converter);
        return restTemplate;
    }
}