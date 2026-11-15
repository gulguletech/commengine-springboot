package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.DailyRewardRequests;
import com.commengine.service.DailyRewardService;
import org.springframework.web.bind.annotation.*;

/** Admin panel: set what each of the 7 days gives. Covered by AdminAuthInterceptor (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/daily-rewards")
public class AdminDailyRewardController {

    private final DailyRewardService service;

    public AdminDailyRewardController(DailyRewardService service) {
        this.service = service;
    }

    @GetMapping
    public Object list() {
        return ApiResponse.ok(service.adminList());
    }

    /** body: { type: NONE|COINS|ITEM, coins?, itemId?, itemDays? } */
    @PutMapping("/{day}")
    public Object set(@PathVariable int day, @RequestBody DailyRewardRequests.SetDay body) {
        return ApiResponse.ok(service.adminSet(day, body.getType(), body.getCoins(), body.getItemId(), body.getItemDays()), "Saved");
    }
}
