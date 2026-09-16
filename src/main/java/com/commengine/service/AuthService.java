package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.CompleteProfileRequest;
import com.commengine.entity.User;
import com.commengine.repository.UserRepository;
import com.commengine.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final Msg91Service msg91Service;

    public AuthService(UserRepository userRepository, JwtService jwtService, Msg91Service msg91Service) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.msg91Service = msg91Service;
    }

    @Transactional
    public Map<String, Object> verifyWidgetToken(String accessToken) {
        Msg91Service.VerifyResult result = msg91Service.verifyWidgetAccessToken(accessToken);
        if (!result.success()) {
            throw new ApiException(result.error() != null ? result.error() : "OTP verification failed", HttpStatus.UNAUTHORIZED);
        }

        String phone = result.phone();
        User existingUser = userRepository.findByPhone(phone).orElse(null);

        Map<String, Object> data = new LinkedHashMap<>();
        if (existingUser != null) {
            existingUser.setOnline(true);
            existingUser.setLastSeen(Instant.now());
            userRepository.save(existingUser);

            String token = jwtService.signAuthToken(existingUser.getId());
            data.put("newUser", false);
            data.put("token", token);
            data.put("user", com.commengine.dto.DtoMapper.userFull(existingUser));
            return data;
        }

        String tempToken = jwtService.signTempToken(phone);
        data.put("newUser", true);
        data.put("tempToken", tempToken);
        return data;
    }

    @Transactional
    public Map<String, Object> completeProfile(String phone, CompleteProfileRequest req) {
        userRepository.findByPhone(phone).ifPresent(u -> {
            throw ApiException.conflict("User already registered, please login instead");
        });

        User user = new User();
        user.setPhone(phone);
        user.setName(req.getName());
        user.setAge(req.getAge());
        user.setGender(req.getGender());
        user.setBio(req.getBio());
        user.setVerified(true);
        user.setOnline(true);
        userRepository.save(user);

        String token = jwtService.signAuthToken(user.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("user", com.commengine.dto.DtoMapper.userFull(user));
        return data;
    }
}
