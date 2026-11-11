package com.commengine.controller;

import com.commengine.common.ApiException;
import com.commengine.common.ApiResponse;
import com.commengine.entity.Admin;
import com.commengine.repository.AdminRepository;
import com.commengine.security.AdminAuthInterceptor;
import com.commengine.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Manage the other admin accounts from inside the panel. */
@RestController
@RequestMapping("/api/admin/admins")
public class AdminAccountsController {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuthService authService;

    public AdminAccountsController(AdminRepository adminRepository, PasswordEncoder passwordEncoder, AdminAuthService authService) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @GetMapping
    public Object list() {
        List<Map<String, Object>> items = adminRepository.findAll().stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(AdminAuthService::toMap).toList();
        return ApiResponse.ok(Map.of("items", items));
    }

    /** Body: { "username": "...", "password": "min 8 chars" } */
    @PostMapping
    @Transactional
    public Object create(@RequestBody Map<String, String> body) {
        String username = body.get("username") == null ? "" : body.get("username").trim();
        if (!AdminAuthService.USERNAME.matcher(username).matches()) {
            throw ApiException.badRequest("Username must be 3-32 characters: letters, numbers, . _ -");
        }
        AdminAuthService.validatePassword(body.get("password"));
        if (adminRepository.existsByUsername(username)) throw ApiException.conflict("That username is already taken");

        Admin a = new Admin();
        a.setUsername(username);
        a.setPasswordHash(passwordEncoder.encode(body.get("password")));
        a.setPasswordChangedAt(Instant.now());
        adminRepository.save(a);
        return ApiResponse.ok(AdminAuthService.toMap(a), "Admin created");
    }

    /** Body: { "password": "new password" } - signs that admin out everywhere. */
    @PostMapping("/{id}/reset-password")
    @Transactional
    public Object resetPassword(@PathVariable String id, @RequestBody Map<String, String> body) {
        Admin a = adminRepository.findById(id).orElseThrow(() -> ApiException.notFound("Admin not found"));
        authService.setPassword(a, body.get("password"));
        return ApiResponse.ok(Map.of("reset", true), "Password reset");
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Object delete(@PathVariable String id, HttpServletRequest request) {
        Admin me = AdminAuthInterceptor.current(request);
        if (me.getId().equals(id)) throw ApiException.badRequest("You can't delete your own account");
        Admin target = adminRepository.findById(id).orElseThrow(() -> ApiException.notFound("Admin not found"));
        if (adminRepository.count() <= 1) throw ApiException.badRequest("You can't delete the last admin");
        adminRepository.delete(target);
        return ApiResponse.ok(Map.of("deleted", true), "Admin deleted");
    }
}
