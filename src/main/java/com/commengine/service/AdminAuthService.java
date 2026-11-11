package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.entity.Admin;
import com.commengine.repository.AdminRepository;
import com.commengine.security.AdminLoginRateLimiter;
import com.commengine.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);
    public static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{3,32}$");
    public static final int MIN_PASSWORD = 8;

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdminLoginRateLimiter rateLimiter;
    /** Compared against when the username does not exist, so both failure paths cost the same time. */
    private final String dummyHash;

    public AdminAuthService(AdminRepository adminRepository, PasswordEncoder passwordEncoder,
                            JwtService jwtService, AdminLoginRateLimiter rateLimiter) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional
    public Map<String, Object> login(String username, String password, String ip) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw ApiException.badRequest("Username and password are required");
        }
        rateLimiter.checkAllowed(ip);

        Admin admin = adminRepository.findByUsername(username.trim()).orElse(null);
        boolean ok = passwordEncoder.matches(password, admin != null ? admin.getPasswordHash() : dummyHash);
        if (admin == null || !ok) {
            rateLimiter.recordFailure(ip);
            log.warn("Admin login failed for '{}' from {}", username, ip);
            // same message whether the username or the password was wrong
            throw ApiException.unauthorized("Invalid username or password");
        }

        rateLimiter.recordSuccess(ip);
        admin.setLastLoginAt(Instant.now());
        adminRepository.save(admin);
        log.info("Admin '{}' logged in from {}", admin.getUsername(), ip);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", jwtService.signAdminToken(admin.getId(), admin.getPasswordChangedAt().getEpochSecond()));
        data.put("expiresInMs", jwtService.getAdminExpirationMs());
        data.put("admin", toMap(admin));
        return data;
    }

    @Transactional
    public Map<String, Object> changeOwnPassword(Admin admin, String currentPassword, String newPassword) {
        Admin fresh = adminRepository.findById(admin.getId()).orElseThrow(() -> ApiException.unauthorized("Admin not found"));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, fresh.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        setPassword(fresh, newPassword);
        // old tokens (carrying the previous passwordChangedAt) die; hand back a new one so this session continues
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", jwtService.signAdminToken(fresh.getId(), fresh.getPasswordChangedAt().getEpochSecond()));
        data.put("expiresInMs", jwtService.getAdminExpirationMs());
        return data;
    }

    public void setPassword(Admin admin, String newPassword) {
        validatePassword(newPassword);
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        admin.setPasswordChangedAt(Instant.now());
        adminRepository.save(admin);
    }

    public static void validatePassword(String pw) {
        if (pw == null || pw.length() < MIN_PASSWORD) {
            throw ApiException.badRequest("Password must be at least " + MIN_PASSWORD + " characters");
        }
        if (pw.length() > 100) throw ApiException.badRequest("Password is too long");
    }

    public static Map<String, Object> toMap(Admin a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("username", a.getUsername());
        m.put("createdAt", a.getCreatedAt());
        m.put("lastLoginAt", a.getLastLoginAt());
        return m;
    }
}
