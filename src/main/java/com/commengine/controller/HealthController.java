package com.commengine.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    // GET /api/health — matches the original app.js's raw (non-ApiResponse) shape exactly.
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "CommEngine API is running");
        body.put("time", Instant.now().toString());
        return body;
    }
}
