package com.commengine.security;

import com.commengine.common.ApiException;
import com.commengine.entity.Admin;
import com.commengine.repository.AdminRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards every /api/admin/** route (except the login). The token must be an ADMIN token, the admin
 * must still exist, and the password must not have changed since the token was issued - so
 * deleting an admin or resetting their password logs them out immediately.
 * A normal app-user token (userId claim, no role) is rejected here.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String ADMIN_ATTR = "commengine.admin";

    private final JwtService jwtService;
    private final AdminRepository adminRepository;

    public AdminAuthInterceptor(JwtService jwtService, AdminRepository adminRepository) {
        this.jwtService = jwtService;
        this.adminRepository = adminRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true; // CORS preflight

        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        if (token == null) throw ApiException.unauthorized("Admin authentication required");

        try {
            Claims claims = jwtService.verify(token);
            if (!"ADMIN".equals(claims.get("role", String.class))) {
                throw ApiException.unauthorized("Admin authentication required");
            }
            String adminId = claims.get("adminId", String.class);
            if (adminId == null) throw ApiException.unauthorized("Admin authentication required");

            Admin admin = adminRepository.findById(adminId)
                    .orElseThrow(() -> ApiException.unauthorized("Admin account no longer exists"));

            Number pca = claims.get("pca", Number.class);
            if (pca == null || pca.longValue() != admin.getPasswordChangedAt().getEpochSecond()) {
                throw ApiException.unauthorized("Session expired, please sign in again");
            }
            request.setAttribute(ADMIN_ATTR, admin);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("Session expired, please sign in again");
        }
    }

    public static Admin current(HttpServletRequest request) {
        Object a = request.getAttribute(ADMIN_ATTR);
        if (a instanceof Admin admin) return admin;
        throw ApiException.unauthorized("Admin authentication required");
    }
}
