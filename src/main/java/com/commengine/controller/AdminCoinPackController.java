package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.CreateCoinPackRequest;
import com.commengine.dto.UpdateCoinPackRequest;
import com.commengine.service.CoinPackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Admin panel: manage coin packs. Covered by AdminAuthInterceptor (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/coin-packs")
public class AdminCoinPackController {

    private final CoinPackService service;

    public AdminCoinPackController(CoinPackService service) {
        this.service = service;
    }

    /** All packs including inactive ones: { items: [{ id, coins, priceInr, active, sortOrder, ... }] } */
    @GetMapping
    public Object list() {
        return ApiResponse.ok(Map.of("items", service.adminList()));
    }

    /** JSON: { coins, priceInr, sortOrder?, active? } */
    @PostMapping
    public Object create(@Valid @RequestBody CreateCoinPackRequest body) {
        return ApiResponse.ok(service.adminCreate(body), "Coin pack added", HttpStatus.CREATED);
    }

    /** JSON, every field optional: { coins, priceInr, sortOrder, active } */
    @PatchMapping("/{id}")
    public Object update(@PathVariable String id, @Valid @RequestBody UpdateCoinPackRequest body) {
        return ApiResponse.ok(service.adminUpdate(id, body), "Coin pack updated");
    }

    @DeleteMapping("/{id}")
    public Object delete(@PathVariable String id) {
        service.adminDelete(id);
        return ApiResponse.ok(Map.of("deleted", true), "Coin pack deleted");
    }
}
