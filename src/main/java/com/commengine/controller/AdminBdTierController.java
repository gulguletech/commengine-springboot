package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.TeamRequests;
import com.commengine.service.BdService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Admin panel: the BD commission ladder. */
@RestController
@RequestMapping("/api/admin/bd-tiers")
public class AdminBdTierController {

    private final BdService bd;

    public AdminBdTierController(BdService bd) {
        this.bd = bd;
    }

    @GetMapping
    public Object list() {
        return ApiResponse.ok(Map.of("items", bd.adminTiers()));
    }

    @PostMapping
    public Object create(@Valid @RequestBody TeamRequests.TierCreate body) {
        return ApiResponse.ok(bd.adminCreateTier(body.getMinSalary(), body.getCommission()), "Tier added", HttpStatus.CREATED);
    }

    @PatchMapping("/{id}")
    public Object update(@PathVariable String id, @Valid @RequestBody TeamRequests.TierUpdate body) {
        return ApiResponse.ok(bd.adminUpdateTier(id, body.getMinSalary(), body.getCommission()), "Tier updated");
    }

    @DeleteMapping("/{id}")
    public Object delete(@PathVariable String id) {
        bd.adminDeleteTier(id);
        return ApiResponse.ok(Map.of("deleted", true), "Tier deleted");
    }
}
