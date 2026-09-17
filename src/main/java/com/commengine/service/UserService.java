package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.DtoMapper;
import com.commengine.dto.UpdateMeRequest;
import com.commengine.entity.User;
import com.commengine.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
// Not strictly required today (User has no lazy relations in its DTO shapes), but added
// for consistency and to guard against future lazy fields — see AudioRoomService for why.
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    public UserService(UserRepository userRepository, CloudinaryService cloudinaryService) {
        this.userRepository = userRepository;
        this.cloudinaryService = cloudinaryService;
    }

    @Transactional
    public Map<String, Object> updateMe(User user, UpdateMeRequest req) {
        if (req.getName() != null) user.setName(req.getName());
        if (req.getBio() != null) user.setBio(req.getBio());
        if (req.getGender() != null) user.setGender(req.getGender());
        userRepository.save(user);
        return Map.of("user", DtoMapper.userFull(user));
    }

    @Transactional
    public Map<String, Object> updateAvatar(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.unprocessable("No file uploaded");
        }
        CloudinaryService.UploadResult result = cloudinaryService.upload(file);
        user.setAvatarUrl(result.url());
        userRepository.save(user);
        return Map.of("user", DtoMapper.userFull(user));
    }

    public Map<String, Object> getUserById(String id) {
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        return Map.of("user", DtoMapper.userProfile(user));
    }

    public Map<String, Object> searchUsers(User currentUser, String search) {
        String trimmed = search == null ? "" : search.trim();
        List<User> users = userRepository.searchUsers(currentUser.getId(), trimmed, PageRequest.of(0, 30));
        return Map.of("users", users.stream().map(DtoMapper::userWithOnline).collect(Collectors.toList()));
    }
}