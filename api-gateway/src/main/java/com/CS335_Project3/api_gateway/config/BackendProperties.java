// api-gateway/src/main/java/com/CS335_Project3/api_gateway/config/GatewayProperties.java

package com.CS335_Project3.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "backend")
public class BackendProperties {
    private String url;
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
