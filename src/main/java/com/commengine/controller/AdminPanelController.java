package com.commengine.controller;

import com.commengine.common.ApiException;
import com.commengine.common.ApiResponse;
import com.commengine.entity.Admin;
import com.commengine.security.AdminAuthInterceptor;
import com.commengine.service.AdminPanelService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Dashboard, users, rooms and transactions for the admin panel. Every route needs an admin token (see AdminAuthInterceptor). */
@RestController
@RequestMapping("/api/admin")
public class AdminPanelController {

    private final AdminPanelService panel;

    public AdminPanelController(AdminPanelService panel) {
        this.panel = panel;
    }

    @GetMapping("/dashboard")
    public Object dashboard() {
        return ApiResponse.ok(panel.dashboard());
    }

    // ---- users ----

    @GetMapping("/users")
    public Object users(@RequestParam(defaultValue = "") String q,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(panel.listUsers(q, page, size));
    }

    @GetMapping("/users/{id}")
    public Object user(@PathVariable String id) {
        return ApiResponse.ok(panel.userDetail(id));
    }

    /** Body: { "currency": "COINS"|"BEANS", "amount": 500 (negative to take away), "note": "..." } */
    @PostMapping("/users/{id}/wallet")
    public Object adjustWallet(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        Admin admin = AdminAuthInterceptor.current(request);
        Object a = body.get("amount");
        if (!(a instanceof Number)) throw ApiException.badRequest("amount must be a number");
        Object cur = body.get("currency");
        Object note = body.get("note");
        return ApiResponse.ok(panel.adjustWallet(admin, id, cur == null ? null : cur.toString(),
                ((Number) a).longValue(), note == null ? null : note.toString()), "Wallet updated");
    }

    /** Body: { "reason": "optional" } */
    @PostMapping("/users/{id}/ban")
    public Object ban(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Object reason = body == null ? null : body.get("reason");
        panel.setBanned(id, true, reason == null ? null : reason.toString());
        return ApiResponse.ok(Map.of("banned", true), "User banned");
    }

    @PostMapping("/users/{id}/unban")
    public Object unban(@PathVariable String id) {
        panel.setBanned(id, false, null);
        return ApiResponse.ok(Map.of("banned", false), "User unbanned");
    }

    // ---- rooms ----

    @GetMapping("/rooms")
    public Object rooms() {
        return ApiResponse.ok(panel.listRooms());
    }

    @PostMapping("/rooms/audio/{id}/end")
    public Object endAudio(@PathVariable String id) {
        panel.endAudioRoom(id);
        return ApiResponse.ok(Map.of("ended", true), "Room ended");
    }

    @PostMapping("/rooms/live/{id}/end")
    public Object endLive(@PathVariable String id) {
        panel.endLiveRoom(id);
        return ApiResponse.ok(Map.of("ended", true), "Live stream ended");
    }

    // ---- transactions ----

    @GetMapping("/transactions")
    public Object transactions(@RequestParam(required = false) String type,
                               @RequestParam(required = false) String userId,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(panel.listTransactions(type, userId, page, size));
    }
}
