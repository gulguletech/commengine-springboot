package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.RankingService;
import org.springframework.web.bind.annotation.*;

/** Star list / Contribution list. Needs a logged-in user (AuthInterceptor covers /api/**). */
@RestController
@RequestMapping("/api/rankings")
public class RankingController {

    private final RankingService service;

    public RankingController(RankingService service) {
        this.service = service;
    }

    /** type = star | contribution, period = today | week | month */
    @GetMapping
    public Object get(@CurrentUser User user,
                      @RequestParam(defaultValue = "star") String type,
                      @RequestParam(defaultValue = "week") String period) {
        return ApiResponse.ok(service.ranking(user, type, period));
    }
}
