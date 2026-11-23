package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.BattleInviteRequest;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.BattleInviteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** PK: invite anyone who is online in the app to join the room (host / room admin only). */
@RestController
@RequestMapping("/api/rooms/audio/{roomId}/battle-invites")
public class BattleInviteController {

    private final BattleInviteService service;

    public BattleInviteController(BattleInviteService service) {
        this.service = service;
    }

    @GetMapping("/candidates")
    public Object candidates(@CurrentUser User user, @PathVariable String roomId, @RequestParam(defaultValue = "") String q) {
        return ApiResponse.ok(Map.of("users", service.candidates(roomId, user, q)));
    }

    @PostMapping
    public Object invite(@CurrentUser User user, @PathVariable String roomId, @Valid @RequestBody BattleInviteRequest body) {
        service.invite(roomId, user, body.getUserId());
        return ApiResponse.ok(Map.of("sent", true), "Invitation sent");
    }
}
