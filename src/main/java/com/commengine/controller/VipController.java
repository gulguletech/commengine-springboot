package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.VipService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** VIP membership as the app sees it. Needs a logged-in user (AuthInterceptor covers /api/**). */
@RestController
@RequestMapping("/api/vip")
public class VipController {

    private final VipService service;

    public VipController(VipService service) {
        this.service = service;
    }

    /** Am I VIP, until when, the plans with their prices, my coins. */
    @GetMapping
    public Object status(@CurrentUser User user) {
        return ApiResponse.ok(service.status(user));
    }

    /** body: { days: 1 | 7 | 30 | 180 | 365 }. Pays with coins. */
    @PostMapping("/buy")
    public Object buy(@CurrentUser User user, @RequestBody Map<String, Integer> body) {
        Integer days = body.get("days");
        if (days == null) throw com.commengine.common.ApiException.badRequest("days is required");
        return ApiResponse.ok(service.buy(user, days), "You are VIP now");
    }

    /** multipart: image. VIP members only. */
    @PostMapping(value = "/background", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object background(@CurrentUser User user, @RequestParam("image") MultipartFile image) {
        return ApiResponse.ok(service.uploadBackground(user, image), "Background saved");
    }
}
