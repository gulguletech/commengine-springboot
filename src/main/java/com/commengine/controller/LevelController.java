package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.LevelService;
import org.springframework.web.bind.annotation.*;

/** The level system as the app sees it. Needs a logged-in user (AuthInterceptor covers /api/**). */
@RestController
@RequestMapping("/api/levels")
public class LevelController {

    private final LevelService service;

    public LevelController(LevelService service) {
        this.service = service;
    }

    /** My level, my progress to the next one, and all 10 levels with their rewards. */
    @GetMapping
    public Object status(@CurrentUser User user) {
        return ApiResponse.ok(service.status(user));
    }

    /** Collect the reward of a level I have reached. */
    @PostMapping("/{level}/claim")
    public Object claim(@CurrentUser User user, @PathVariable int level) {
        return ApiResponse.ok(service.claim(user, level), "Reward collected");
    }
}
