package com.commengine.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal fixed-window rate limiter (in-memory, single-instance) mirroring the
 * express-rate-limit config on POST /api/auth/verify-widget-token: 15 requests
 * per 10 minutes per client key (IP address).
 *
 * Note: this resets on restart and does not share state across instances —
 * for multi-instance deployments, back this with Redis instead.
 */
@Component
public class SimpleRateLimiter {

    private record Window(Instant windowStart, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int maxRequests, Duration windowSize) {
        Instant now = Instant.now();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || Duration.between(existing.windowStart(), now).compareTo(windowSize) > 0) {
                return new Window(now, new AtomicInteger(0));
            }
            return existing;
        });
        return window.count().incrementAndGet() <= maxRequests;
    }
}
