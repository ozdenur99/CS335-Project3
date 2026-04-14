package com.cs335.backendservice;

import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//receives log entries forwarded from the API gateway in real time
@RestController
@RequestMapping("/api/logs")
public class LogController {

    //stores received logs in memory (resets when restarted)
    private final List<Map<String, Object>> receivedLogs = new ArrayList<>();

    //POST /api/logs (receives a log entry from the gateway)
    @PostMapping
    public String receiveLog(@RequestBody Map<String, Object> logEntry) {
        receivedLogs.add(logEntry);
        return "ok";
    }

    //GET /api/logs (returns all logs received from the gateway)
    @GetMapping
    public List<Map<String, Object>> getLogs() {
        return receivedLogs;
    }
}