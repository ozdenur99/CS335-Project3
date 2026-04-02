package com.CS335_Project3.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// import java.util.ArrayList;
import java.util.List;
import hashset;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "gateway")
public class ApiKeyConfig {

    // Use set to prevent duplicate keys. 
    // If a duplicate key is added, it will be ignored.
    private List<String> apiKeys = new HashSet<>();

    public List<String> getApiKeys() {
        return apiKeys;
    }

    // public void setApiKeys(List<String> apiKeys) {
    //     if (apiKeys == null || apiKeys.isEmpty()) {
    //         this.apiKeys = new ArrayList<>(DEFAULT_KEYS);
    //     } else {
    //         this.apiKeys = apiKeys;
    //     }
    // }

    public void setApiKeys(Set<String> apiKeys) {
        this.apiKeys = apiKeys != null ? apiKeys : new HashSet<>();
    }

    public boolean isValidKey(String key) {
        if (key == null || key.isBlank()) return false;
        // HashSet lookup is O(1) on average, while ArrayList lookup is O(n). 
        // Since we only need to check if a key exists, HashSet is a better choice.
        return apiKeys.contains(key.toLowerCase());
        // return apiKeys.stream()
        //     .map(String::toLowerCase)
        //     .toList()
        //     .contains(key.toLowerCase());
    }
}

