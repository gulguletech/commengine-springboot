package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.SuperAdminRequests;
import com.commengine.service.BadgeService;
import com.commengine.service.SuperAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Admin panel: who the super admins are (with their salary / commission / targets) and the badge shelf. */
@RestController
@RequestMapping("/api/admin")
public class AdminSuperAdminController {

    private final SuperAdminService superAdmins;
    private final BadgeService badges;

    public AdminSuperAdminController(SuperAdminService superAdmins, BadgeService badges) {
        this.superAdmins = superAdmins;
        this.badges = badges;
    }

    // ---------------------------------------------------------------- super admins
    @GetMapping("/super-admins")
    public Object list() {
        return ApiResponse.ok(Map.of("items", superAdmins.adminList()));
    }

    @PostMapping("/super-admins")
    public Object add(@Valid @RequestBody SuperAdminRequests.Create body) {
        return ApiResponse.ok(superAdmins.adminAdd(body), "Super admin added", HttpStatus.CREATED);
    }

    @PatchMapping("/super-admins/{userId}")
    public Object update(@PathVariable String userId, @Valid @RequestBody SuperAdminRequests.Update body) {
        superAdmins.adminUpdate(userId, body);
        return ApiResponse.ok(Map.of("updated", true), "Saved");
    }

    @DeleteMapping("/super-admins/{userId}")
    public Object remove(@PathVariable String userId) {
        superAdmins.adminRemove(userId);
        return ApiResponse.ok(Map.of("removed", true), "Super admin removed");
    }

    // ---------------------------------------------------------------- badges
    @GetMapping("/badges")
    public Object badgeList() {
        return ApiResponse.ok(Map.of("items", badges.adminList()));
    }

    /** multipart: name, image (required), sortOrder (optional) */
    @PostMapping(value = "/badges", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object badgeCreate(@RequestParam("name") String name,
                              @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
                              @RequestParam("image") MultipartFile image) {
        return ApiResponse.ok(badges.adminCreate(name, sortOrder, image), "Badge added");
    }

    /** multipart, every part optional: name, sortOrder, active, image */
    @PostMapping(value = "/badges/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object badgeUpdate(@PathVariable String id,
                              @RequestParam(value = "name", required = false) String name,
                              @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
                              @RequestParam(value = "active", required = false) Boolean active,
                              @RequestParam(value = "image", required = false) MultipartFile image) {
        return ApiResponse.ok(badges.adminUpdate(id, name, sortOrder, active, image), "Badge updated");
    }

    @DeleteMapping("/badges/{id}")
    public Object badgeDelete(@PathVariable String id) {
        badges.adminDelete(id);
        return ApiResponse.ok(Map.of("deleted", true), "Badge deleted");
    }
}
