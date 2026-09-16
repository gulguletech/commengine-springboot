package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.RespondRequest;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.FriendService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @PostMapping("/request/{userId}")
    public Object sendRequest(@CurrentUser User user, @PathVariable String userId) {
        var data = friendService.sendRequest(user, userId);
        String status = String.valueOf(data.get("status"));
        String message = switch (status) {
            case "ACCEPTED" -> "You're now friends";
            case "PENDING" -> "Friend request sent";
            default -> "Success";
        };
        return ApiResponse.ok(data, message);
    }

    @PostMapping("/requests/{requestId}/respond")
    public Object respondRequest(@CurrentUser User user, @PathVariable String requestId, @Valid @RequestBody RespondRequest body) {
        var data = friendService.respondRequest(user, requestId, Boolean.TRUE.equals(body.getAccept()));
        String status = String.valueOf(data.get("status"));
        return ApiResponse.ok(data, "Friend request " + status.toLowerCase());
    }

    @GetMapping("/requests")
    public Object listIncomingRequests(@CurrentUser User user) {
        return ApiResponse.ok(friendService.listIncomingRequests(user));
    }

    @GetMapping("/status/{userId}")
    public Object getStatus(@CurrentUser User user, @PathVariable String userId) {
        return ApiResponse.ok(friendService.getStatus(user, userId));
    }

    @GetMapping
    public Object listFriends(@CurrentUser User user) {
        return ApiResponse.ok(friendService.listFriends(user));
    }
}
