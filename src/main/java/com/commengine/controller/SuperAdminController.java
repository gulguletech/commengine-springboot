package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.SuperAdminRequests;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.SuperAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** App: the super admin portal. Every method re-checks that the caller is a super admin. */
@RestController
@RequestMapping("/api/super-admin")
public class SuperAdminController {

    private final SuperAdminService service;

    public SuperAdminController(SuperAdminService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public Object dashboard(@CurrentUser User user) {
        return ApiResponse.ok(service.dashboard(user));
    }

    @GetMapping("/team/agencies")
    public Object agencies(@CurrentUser User user, @RequestParam(defaultValue = "month") String period,
                           @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ApiResponse.ok(service.teamAgencies(user, period, from, to));
    }

    @GetMapping("/team/hosts")
    public Object hosts(@CurrentUser User user, @RequestParam(defaultValue = "month") String period,
                        @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ApiResponse.ok(service.teamHosts(user, period, from, to));
    }

    @GetMapping("/reports")
    public Object reports(@CurrentUser User user, @RequestParam(defaultValue = "day") String period,
                          @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ApiResponse.ok(service.reports(user, period, from, to));
    }

    @GetMapping("/wares")
    public Object wares(@CurrentUser User user, @RequestParam(defaultValue = "") String q) {
        return ApiResponse.ok(Map.of("items", service.wares(user, q)));
    }

    @PostMapping("/wares/give")
    public Object giveWare(@CurrentUser User user, @Valid @RequestBody SuperAdminRequests.GiveWare body) {
        return ApiResponse.ok(service.giveWare(user, body), "Item given");
    }

    @GetMapping("/badges")
    public Object badges(@CurrentUser User user) {
        return ApiResponse.ok(Map.of("items", service.badges(user)));
    }

    @PostMapping("/badges/give")
    public Object giveBadge(@CurrentUser User user, @Valid @RequestBody SuperAdminRequests.GiveBadge body) {
        return ApiResponse.ok(service.giveBadge(user, body), "Badge given");
    }

    @GetMapping("/dedicated")
    public Object dedicated(@CurrentUser User user, @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "ALL") String kind) {
        return ApiResponse.ok(Map.of("items", service.dedicated(user, q, kind)));
    }

    @DeleteMapping("/dedicated/{id}")
    public Object revoke(@CurrentUser User user, @PathVariable String id) {
        service.revoke(user, id);
        return ApiResponse.ok(Map.of("revoked", true), "Taken back");
    }
}
