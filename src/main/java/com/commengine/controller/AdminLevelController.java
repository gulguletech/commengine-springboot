package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.LevelRequests;
import com.commengine.service.LevelService;
import org.springframework.web.bind.annotation.*;

/** Admin panel: set the gift amount and the reward for each of the 10 levels. Covered by AdminAuthInterceptor. */
@RestController
@RequestMapping("/api/admin/levels")
public class AdminLevelController {

    private final LevelService service;

    public AdminLevelController(LevelService service) {
        this.service = service;
    }

    @GetMapping
    public Object list() {
        return ApiResponse.ok(service.adminList());
    }

    /** body: { requiredCoins, type: NONE|COINS|ITEM, coins?, itemId?, itemDays? } */
    @PutMapping("/{level}")
    public Object set(@PathVariable int level, @RequestBody LevelRequests.SetLevel b) {
        return ApiResponse.ok(service.adminSet(level, b.getRequiredCoins(), b.getType(), b.getCoins(), b.getItemId(), b.getItemDays()), "Saved");
    }
}
