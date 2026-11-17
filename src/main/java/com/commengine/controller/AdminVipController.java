package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.service.VipService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Admin panel: set the price of each VIP plan. Covered by AdminAuthInterceptor (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/vip")
public class AdminVipController {

    private final VipService service;

    public AdminVipController(VipService service) {
        this.service = service;
    }

    @GetMapping
    public Object list() {
        return ApiResponse.ok(service.adminList());
    }

    /** body: { priceCoins?, active? } */
    @PutMapping("/plans/{days}")
    public Object set(@PathVariable int days, @RequestBody Map<String, Object> body) {
        Long price = body.get("priceCoins") == null ? null : ((Number) body.get("priceCoins")).longValue();
        Boolean active = body.get("active") == null ? null : Boolean.valueOf(String.valueOf(body.get("active")));
        return ApiResponse.ok(service.adminSet(days, price, active), "Saved");
    }
}
