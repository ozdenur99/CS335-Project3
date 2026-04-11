package com.CS335_Project3.api_gateway.controller;

import com.CS335_Project3.api_gateway.config.RuntimeRateLimitConfigService;
import com.CS335_Project3.api_gateway.config.TenantRateLimitConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/config")
public class ConfigController {

    private final RuntimeRateLimitConfigService runtimeRateLimitConfigService;

    public ConfigController(RuntimeRateLimitConfigService runtimeRateLimitConfigService) {
        this.runtimeRateLimitConfigService = runtimeRateLimitConfigService;
    }

    @GetMapping("/rate-limit")
    public TenantRateLimitConfig getRateLimitConfig() {
        return runtimeRateLimitConfigService.getEffectiveConfig();
    }

    @GetMapping("/rate-limit/download")
    public ResponseEntity<byte[]> downloadRateLimitConfig() throws Exception {
        byte[] body = runtimeRateLimitConfigService.getEffectiveConfigJson().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rate-limit-config.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @PostMapping("/rate-limit")
    public TenantRateLimitConfig uploadRateLimitConfig(@RequestBody TenantRateLimitConfig config) throws Exception {
        return runtimeRateLimitConfigService.saveConfig(config);
    }

    @PostMapping(value = "/rate-limit/upload", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TenantRateLimitConfig uploadRateLimitConfigRaw(@RequestBody String rawJson) throws Exception {
        return runtimeRateLimitConfigService.saveConfigJson(rawJson);
    }
}

