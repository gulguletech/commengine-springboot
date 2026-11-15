package com.commengine.controller;

import com.commengine.common.ApiException;
import com.commengine.common.ApiResponse;
import com.commengine.dto.StoreRequests;
import com.commengine.entity.StoreCategory;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.StoreService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** The store, as the app sees it. All routes need a logged-in user (covered by AuthInterceptor on /api/**). */
@RestController
@RequestMapping("/api/store")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    /** GET /api/store/items?category=FRAME|BUBBLE|BACKGROUND|ENTRY_EFFECT -> { items, durations, wallet } */
    @GetMapping("/items")
    public Object items(@CurrentUser User user, @RequestParam("category") String category) {
        return ApiResponse.ok(storeService.catalog(user, parse(category)));
    }

    /** What the user owns right now + what they are wearing. */
    @GetMapping("/backpack")
    public Object backpack(@CurrentUser User user) {
        return ApiResponse.ok(storeService.backpack(user));
    }

    /** Backgrounds the user can put in a room: free ones + paid ones they still own. */
    @GetMapping("/backgrounds/usable")
    public Object usableBackgrounds(@CurrentUser User user) {
        return ApiResponse.ok(Map.of("items", storeService.usableBackgrounds(user)));
    }

    @PostMapping("/buy")
    public Object buy(@CurrentUser User user, @Valid @RequestBody StoreRequests.Buy body) {
        return ApiResponse.ok(storeService.buy(user, body.getItemId(), body.getDays()), "Purchased");
    }

    @PostMapping("/send")
    public Object send(@CurrentUser User user, @Valid @RequestBody StoreRequests.Send body) {
        return ApiResponse.ok(storeService.send(user, body.getItemId(), body.getDays(), body.getReceiverId()), "Sent");
    }

    @PostMapping("/equip")
    public Object equip(@CurrentUser User user, @Valid @RequestBody StoreRequests.Equip body) {
        return ApiResponse.ok(storeService.equip(user, body.getItemId()), "Equipped");
    }

    @PostMapping("/unequip")
    public Object unequip(@CurrentUser User user, @Valid @RequestBody StoreRequests.Unequip body) {
        return ApiResponse.ok(storeService.unequip(user, parse(body.getCategory())), "Removed");
    }

    private static StoreCategory parse(String category) {
        try {
            return StoreCategory.valueOf(category.trim().toUpperCase());
        } catch (Exception e) {
            throw ApiException.badRequest("category must be FRAME, BUBBLE, BACKGROUND or ENTRY_EFFECT");
        }
    }
}
