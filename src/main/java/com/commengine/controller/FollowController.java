package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.FollowService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/follows")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/{userId}")
    public Object follow(@CurrentUser User user, @PathVariable String userId) {
        return ApiResponse.ok(followService.follow(user, userId), "Following");
    }

    @DeleteMapping("/{userId}")
    public Object unfollow(@CurrentUser User user, @PathVariable String userId) {
        return ApiResponse.ok(followService.unfollow(user, userId), "Unfollowed");
    }

    /** Am I following them / do they follow me + their counts. */
    @GetMapping("/status/{userId}")
    public Object status(@CurrentUser User user, @PathVariable String userId) {
        return ApiResponse.ok(followService.status(user.getId(), userId));
    }

    /** People `userId` follows. */
    @GetMapping("/{userId}/following")
    public Object following(@PathVariable String userId) {
        return ApiResponse.ok(followService.following(userId));
    }

    /** People who follow `userId`. */
    @GetMapping("/{userId}/followers")
    public Object followers(@PathVariable String userId) {
        return ApiResponse.ok(followService.followers(userId));
    }
}
