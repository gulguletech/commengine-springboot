package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.DailyRewardService;
import org.springframework.web.bind.annotation.*;

/** The 7-day reward as the app sees it. Needs a logged-in user (AuthInterceptor covers /api/**). */
@RestController
@RequestMapping("/api/rewards/daily")
public class DailyRewardController {

    private final DailyRewardService service;

    public DailyRewardController(DailyRewardService service) {
        this.service = service;
    }

    /** The 7 days, which ones are collected, and whether today's can be collected. */
    @GetMapping
    public Object status(@CurrentUser User user) {
        return ApiResponse.ok(service.status(user));
    }

    /** Collect today's reward. */
    @PostMapping("/claim")
    public Object claim(@CurrentUser User user) {
        return ApiResponse.ok(service.claim(user), "Reward collected");
    }
}
