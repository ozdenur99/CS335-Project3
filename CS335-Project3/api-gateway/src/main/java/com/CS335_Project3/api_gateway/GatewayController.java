package com.CS335_Project3.api_gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class GatewayController {

    private final String BACKEND = "http://localhost:8081";

    @GetMapping("/gateway/hello")
    public String forwardHello() {
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(BACKEND + "/hello", String.class);
    }
}