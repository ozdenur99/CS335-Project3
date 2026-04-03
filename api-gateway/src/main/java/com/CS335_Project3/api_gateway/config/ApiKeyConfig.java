package com.CS335_Project3.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;


import java.util.ArrayList;
import java.util.List;


@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "gateway")
public class ApiKeyConfig {


    // Fallback keys used when application.properties is missing or api-keys is empty.
    // These are for local development only — never put real keys here.
    private static final List<String> DEFAULT_KEYS =
        List.of("dev-key-alpha", "dev-key-beta");


    private List<String> apiKeys = new ArrayList<>(DEFAULT_KEYS);


    public List<String> getApiKeys() {
        return apiKeys;
    }


    public void setApiKeys(List<String> apiKeys) {
        if (apiKeys == null || apiKeys.isEmpty()) {
            this.apiKeys = new ArrayList<>(DEFAULT_KEYS);
        } else {
            this.apiKeys = apiKeys;
        }
    }


    // Normalises to lowercase so key matching is not case-sensitive.
    // key-Alpha and key-alpha are treated as the same key.
    public boolean isValidKey(String key) {
        if (key == null || key.isBlank()) return false;
        return apiKeys.stream()
            .map(String::toLowerCase)
            .toList()
            .contains(key.toLowerCase());
    }
}

