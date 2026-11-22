package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.AgencyRequests;
import com.commengine.service.AgencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Admin panel: agencies + the salary ladder. Covered by AdminAuthInterceptor (/api/admin/**). */
@RestController
@RequestMapping("/api/admin")
public class AdminAgencyController {

    private final AgencyService service;

    public AdminAgencyController(AgencyService service) {
        this.service = service;
    }

    // ---------------------------------------------------------------- agencies
    @GetMapping("/agencies")
    public Object list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "15") int size) {
        return ApiResponse.ok(service.adminList(q, page, size));
    }

    @GetMapping("/agencies/{id}")
    public Object get(@PathVariable String id) {
        return ApiResponse.ok(service.adminGet(id));
    }

    @PostMapping("/agencies")
    public Object create(@Valid @RequestBody AgencyRequests.AdminCreate body) {
        return ApiResponse.ok(service.adminCreate(body), "Agency created", HttpStatus.CREATED);
    }

    @PatchMapping("/agencies/{id}")
    public Object update(@PathVariable String id, @Valid @RequestBody AgencyRequests.AdminUpdate body) {
        return ApiResponse.ok(service.adminUpdate(id, body), "Agency updated");
    }

    @DeleteMapping("/agencies/{id}")
    public Object delete(@PathVariable String id) {
        service.adminDelete(id);
        return ApiResponse.ok(Map.of("deleted", true), "Agency deleted");
    }

    @PostMapping("/agencies/{id}/members")
    public Object addMember(@PathVariable String id, @Valid @RequestBody AgencyRequests.AdminAddMember body) {
        service.adminAddMember(id, body.getUserId());
        return ApiResponse.ok(Map.of("added", true), "Host added", HttpStatus.CREATED);
    }

    @DeleteMapping("/agencies/{id}/members/{userId}")
    public Object removeMember(@PathVariable String id, @PathVariable String userId) {
        service.adminRemoveMember(id, userId);
        return ApiResponse.ok(Map.of("removed", true), "Host removed");
    }

    // ---------------------------------------------------------------- levels
    @GetMapping("/agency-levels")
    public Object levels() {
        return ApiResponse.ok(Map.of("items", service.adminLevels()));
    }

    @PostMapping("/agency-levels")
    public Object createLevel(@Valid @RequestBody AgencyRequests.LevelCreate body) {
        return ApiResponse.ok(service.adminCreateLevel(body), "Level added", HttpStatus.CREATED);
    }

    @PatchMapping("/agency-levels/{id}")
    public Object updateLevel(@PathVariable String id, @Valid @RequestBody AgencyRequests.LevelUpdate body) {
        return ApiResponse.ok(service.adminUpdateLevel(id, body), "Level updated");
    }

    @DeleteMapping("/agency-levels/{id}")
    public Object deleteLevel(@PathVariable String id) {
        service.adminDeleteLevel(id);
        return ApiResponse.ok(Map.of("deleted", true), "Level deleted");
    }
}
