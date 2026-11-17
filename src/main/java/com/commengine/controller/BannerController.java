package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.service.EventBannerService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Event banners for the Home screen. Needs a logged-in user (AuthInterceptor covers /api/**). */
@RestController
@RequestMapping("/api/banners")
public class BannerController {

    private final EventBannerService service;

    public BannerController(EventBannerService service) {
        this.service = service;
    }

    @GetMapping
    public Object list() {
        return ApiResponse.ok(Map.of("banners", service.active()));
    }
}
