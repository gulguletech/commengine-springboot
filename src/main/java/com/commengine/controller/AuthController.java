package com.commengine.controller;

import com.commengine.common.ApiException;
import com.commengine.common.ApiResponse;
import com.commengine.dto.CompleteProfileRequest;
import com.commengine.dto.DtoMapper;
import com.commengine.dto.VerifyWidgetTokenRequest;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.security.JwtService;
import com.commengine.security.SimpleRateLimiter;
import com.commengine.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final SimpleRateLimiter rateLimiter;

    public AuthController(AuthService authService, JwtService jwtService, SimpleRateLimiter rateLimiter) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    // POST /api/auth/verify-widget-token  { accessToken } — rate limited: 15 requests / 10 min per IP
    @PostMapping("/verify-widget-token")
    public Object verifyWidgetToken(@Valid @RequestBody VerifyWidgetTokenRequest body,
                                     HttpServletRequest request) {
        String ip = clientIp(request);
        if (!rateLimiter.tryAcquire("verify-widget-token:" + ip, 15, Duration.ofMinutes(10))) {
            return ApiResponse.fail("Too many requests, please try again later", HttpStatus.TOO_MANY_REQUESTS);
        }
        Map<String, Object> data = authService.verifyWidgetToken(body.accessToken());
        boolean newUser = Boolean.TRUE.equals(data.get("newUser"));
        return ApiResponse.ok(data, newUser ? "Phone verified, please complete your profile" : "Login successful");
    }

    // POST /api/auth/complete-profile  (requires temp token)  { name, age, gender?, bio? }
    @PostMapping("/complete-profile")
    public Object completeProfile(@Valid @RequestBody CompleteProfileRequest body, HttpServletRequest request) {
        String phone = requireTempAuth(request);
        Map<String, Object> data = authService.completeProfile(phone, body);
        return ApiResponse.ok(data, "Registration complete", HttpStatus.CREATED);
    }

    // GET /api/auth/me
    @GetMapping("/me")
    public Object me(@CurrentUser User user) {
        return ApiResponse.ok(Map.of("user", DtoMapper.userFull(user)));
    }

    private String requireTempAuth(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        if (token == null) {
            throw new ApiException("Authentication token missing", HttpStatus.UNAUTHORIZED);
        }
        try {
            Claims claims = jwtService.verify(token);
            if (!Boolean.TRUE.equals(claims.get("temp", Boolean.class))) {
                throw new ApiException("Invalid token for this action", HttpStatus.UNAUTHORIZED);
            }
            return claims.get("phone", String.class);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Invalid or expired token", HttpStatus.UNAUTHORIZED);
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
