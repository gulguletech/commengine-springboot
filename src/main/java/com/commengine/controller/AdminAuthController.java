package com.commengine.controller;

import com.commengine.common.ApiException;
import com.commengine.common.ApiResponse;
import com.commengine.entity.Admin;
import com.commengine.security.AdminAuthInterceptor;
import com.commengine.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin sign-in: username + password only (no OTP). Issues a short-lived admin JWT.
 * POST /api/admin/auth/login   { "username": "...", "password": "..." }
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService authService;

    public AdminAuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Object login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        return ApiResponse.ok(authService.login(body.get("username"), body.get("password"), clientIp(request)), "Signed in");
    }

    @GetMapping("/me")
    public Object me(HttpServletRequest request) {
        Admin admin = AdminAuthInterceptor.current(request);
        return ApiResponse.ok(AdminAuthService.toMap(admin));
    }

    @PostMapping("/change-password")
    public Object changePassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Admin admin = AdminAuthInterceptor.current(request);
        String current = body.get("currentPassword");
        String next = body.get("newPassword");
        if (current == null || next == null) throw ApiException.badRequest("currentPassword and newPassword are required");
        return ApiResponse.ok(authService.changeOwnPassword(admin, current, next), "Password changed");
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For"); // behind Render's proxy
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
