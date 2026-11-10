package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.SendGiftRequest;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.GiftService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/gifts")
public class GiftController {

    private final GiftService giftService;

    public GiftController(GiftService giftService) {
        this.giftService = giftService;
    }

    /** The gift shelf: [{ id, name, emoji, price }] */
    @GetMapping
    public Object catalog() {
        return ApiResponse.ok(Map.of("gifts", giftService.catalog()));
    }

    /** Spend coins on a gift for someone in a room. Returns the sender's new wallet. */
    @PostMapping("/send")
    public Object send(@CurrentUser User user, @Valid @RequestBody SendGiftRequest body) {
        return ApiResponse.ok(giftService.send(user, body), "Gift sent");
    }
}
