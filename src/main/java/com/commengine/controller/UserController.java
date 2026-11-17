package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.UpdateMeRequest;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.UserDetailsService;
import com.commengine.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserDetailsService detailsService;

    public UserController(UserService userService, UserDetailsService detailsService) {
        this.userService = userService;
        this.detailsService = detailsService;
    }

    // GET /api/users?search=xyz
    @GetMapping
    public Object searchUsers(@CurrentUser User user, @RequestParam(required = false) String search) {
        return ApiResponse.ok(userService.searchUsers(user, search));
    }

    // PATCH /api/users/me
    @PatchMapping("/me")
    public Object updateMe(@CurrentUser User user, @Valid @RequestBody UpdateMeRequest body) {
        return ApiResponse.ok(userService.updateMe(user, body), "Profile updated");
    }

    // POST /api/users/me/avatar  (multipart, field: avatar)
    @PostMapping("/me/avatar")
    public Object updateAvatar(@CurrentUser User user, @RequestParam("avatar") MultipartFile avatar) {
        return ApiResponse.ok(userService.updateAvatar(user, avatar), "Avatar updated");
    }

    // GET /api/users/:id/details  (the full profile card: level, stats, live now)
    @GetMapping("/{id}/details")
    public Object getUserDetails(@PathVariable String id) {
        return ApiResponse.ok(detailsService.details(id));
    }

    // GET /api/users/:id
    @GetMapping("/{id}")
    public Object getUserById(@PathVariable String id) {
        return ApiResponse.ok(userService.getUserById(id));
    }
}
