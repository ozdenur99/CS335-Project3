package com.CS335_Project3.api_gateway.controller;

import com.CS335_Project3.api_gateway.filter.BlockedIps;
import com.CS335_Project3.api_gateway.metrics.BotDetector;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/admin/reset")
public class AdminController {

    private final BotDetector botDetector;
    private final BlockedIps blockedIps;

    public AdminController(BotDetector botDetector, BlockedIps blockedIps) {
        this.botDetector = botDetector;
        this.blockedIps = blockedIps;
    }

    // clears all bot tracking data — resets request counts and suspicious IP flags
    @PostMapping("/bot")
    public Map<String, String> resetBot() {
        botDetector.reset();
        return Map.of("status", "ok", "message", "bot detection data cleared");
    }

    // clears all blocked IPs/keys from the Redis blocklist
    @PostMapping("/blocked")
    public Map<String, String> resetBlocked() {
        blockedIps.reset();
        return Map.of("status", "ok", "message", "blocked clients cleared");
    }

    // clears both at once — useful between demo runs
    @PostMapping("/all")
    public Map<String, String> resetAll() {
        botDetector.reset();
        blockedIps.reset();
        return Map.of("status", "ok", "message", "all abuse and bot data cleared");
    }
}
