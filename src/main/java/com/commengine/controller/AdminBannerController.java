package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.service.EventBannerService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/** Admin panel: add / remove / reorder Home event banners. Covered by AdminAuthInterceptor (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/banners")
public class AdminBannerController {

    private final EventBannerService service;

    public AdminBannerController(EventBannerService service) {
        this.service = service;
    }

    /** The banners plus the exact size the picture must have. */
    @GetMapping
    public Object list() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", service.adminList());
        m.put("spec", service.spec());
        return ApiResponse.ok(m);
    }

    /** multipart: image (required, exact size), title (optional), sortOrder (optional) */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object create(@RequestParam("image") MultipartFile image,
                         @RequestParam(value = "title", required = false) String title,
                         @RequestParam(value = "sortOrder", required = false) Integer sortOrder) {
        return ApiResponse.ok(service.adminCreate(title, sortOrder, image), "Banner added");
    }

    /** multipart, every part optional: title, sortOrder, active, image */
    @PostMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object update(@PathVariable String id,
                         @RequestParam(value = "title", required = false) String title,
                         @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
                         @RequestParam(value = "active", required = false) Boolean active,
                         @RequestParam(value = "image", required = false) MultipartFile image) {
        return ApiResponse.ok(service.adminUpdate(id, title, sortOrder, active, image), "Banner updated");
    }

    @DeleteMapping("/{id}")
    public Object delete(@PathVariable String id) {
        service.adminDelete(id);
        return ApiResponse.ok(Map.of("deleted", true), "Banner deleted");
    }
}
