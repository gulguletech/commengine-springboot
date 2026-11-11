package com.commengine.security;

import com.commengine.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory brute-force guard for the admin login, keyed by client IP: too many failures inside a
 * rolling window locks that IP out for a cooldown. (Counters reset on restart / are per instance -
 * fine for a single backend instance.)
 */
@Component
public class AdminLoginRateLimiter {

    @Value("${admin.login.max-attempts:5}")
    private int maxAttempts;

    @Value("${admin.login.window-minutes:10}")
    private int windowMinutes;

    @Value("${admin.login.lockout-minutes:15}")
    private int lockoutMinutes;

    private final Map<String, Deque<Instant>> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Instant> lockedUntil = new ConcurrentHashMap<>();

    /** Throws 429 if this IP is currently locked out. */
    public void checkAllowed(String ip) {
        Instant until = lockedUntil.get(ip);
        if (until == null) return;
        if (Instant.now().isBefore(until)) {
            long minutesLeft = Math.max(1, Duration.between(Instant.now(), until).toMinutes() + 1);
            throw new ApiException("Too many failed login attempts. Try again in " + minutesLeft + " minute(s).",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        lockedUntil.remove(ip);
        failedAttempts.remove(ip);
    }

    public void recordFailure(String ip) {
        Instant now = Instant.now();
        Deque<Instant> attempts = failedAttempts.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        attempts.addLast(now);
        Instant windowStart = now.minus(Duration.ofMinutes(windowMinutes));
        while (!attempts.isEmpty() && attempts.peekFirst() != null && attempts.peekFirst().isBefore(windowStart)) {
            attempts.pollFirst();
        }
        if (attempts.size() >= maxAttempts) {
            lockedUntil.put(ip, now.plus(Duration.ofMinutes(lockoutMinutes)));
        }
    }

    public void recordSuccess(String ip) {
        failedAttempts.remove(ip);
        lockedUntil.remove(ip);
    }
}
