package com.commengine.security;

import com.commengine.common.ApiException;
import com.commengine.entity.User;
import com.commengine.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Browser CORS preflight (OPTIONS) never carries the Authorization header. Let it through so
        // Spring's CORS handling can answer it; otherwise web-based games get "Failed to fetch".
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        if (token == null) {
            throw new ApiException("Authentication token missing", HttpStatus.UNAUTHORIZED);
        }

        try {
            Claims claims = jwtService.verify(token);
            Boolean temp = claims.get("temp", Boolean.class);
            if (Boolean.TRUE.equals(temp)) {
                throw new ApiException("Please complete your profile first", HttpStatus.UNAUTHORIZED);
            }
            String userId = claims.get("userId", String.class);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ApiException("User not found", HttpStatus.UNAUTHORIZED));
            request.setAttribute(CurrentUserArgumentResolver.REQUEST_ATTR, user);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException("Invalid or expired token", HttpStatus.UNAUTHORIZED);
        }
    }
}