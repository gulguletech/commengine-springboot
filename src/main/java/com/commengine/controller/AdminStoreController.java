package com.commengine.controller;

import com.commengine.common.ApiException;
import com.commengine.common.ApiResponse;
import com.commengine.entity.StoreCategory;
import com.commengine.service.StoreService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Admin panel: manage the store shelf. Covered by AdminAuthInterceptor (/api/admin/**).
 * Create / update are multipart so the artwork uploads in the same request. Every file is checked against the strict
 * rules in StoreMediaValidator BEFORE it is uploaded; a rejected file returns 422 with a message saying what to fix.
 */
@RestController
@RequestMapping("/api/admin/store")
public class AdminStoreController {

    private final StoreService storeService;

    public AdminStoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    /** The upload rules (sizes, formats, limits) exactly as the server enforces them. */
    @GetMapping("/specs")
    public Object specs() {
        return ApiResponse.ok(storeService.adminSpecs());
    }

    @GetMapping("/items")
    public Object list(@RequestParam(value = "category", required = false) String category) {
        return ApiResponse.ok(Map.of("items", storeService.adminList(category == null || category.isBlank() ? null : parse(category))));
    }

    /**
     * multipart:
     *   category (FRAME|BUBBLE|BACKGROUND|ENTRY_EFFECT), name, free (true/false), pricePerDay (coins, when not free),
     *   sortOrder?, textColor? (BUBBLE, #RRGGBB), scrim? (BACKGROUND, 0..0.8)
     *   files: image (FRAME / BUBBLE / BACKGROUND)  |  preview + video (ENTRY_EFFECT)
     */
    @PostMapping(value = "/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object create(@RequestParam("category") String category,
                         @RequestParam("name") String name,
                         @RequestParam(value = "free", defaultValue = "false") boolean free,
                         @RequestParam(value = "pricePerDay", required = false) Long pricePerDay,
                         @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
                         @RequestParam(value = "textColor", required = false) String textColor,
                         @RequestParam(value = "scrim", required = false) Double scrim,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         @RequestParam(value = "preview", required = false) MultipartFile preview,
                         @RequestParam(value = "video", required = false) MultipartFile video) {
        return ApiResponse.ok(storeService.adminCreate(parse(category), name, free, pricePerDay, sortOrder, textColor, scrim,
                image, preview, video), "Item added");
    }

    /** multipart, every part optional. Sending a file replaces that file (and is validated like a new upload). */
    @PostMapping(value = "/items/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object update(@PathVariable String id,
                         @RequestParam(value = "name", required = false) String name,
                         @RequestParam(value = "free", required = false) Boolean free,
                         @RequestParam(value = "pricePerDay", required = false) Long pricePerDay,
                         @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
                         @RequestParam(value = "active", required = false) Boolean active,
                         @RequestParam(value = "textColor", required = false) String textColor,
                         @RequestParam(value = "scrim", required = false) Double scrim,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         @RequestParam(value = "preview", required = false) MultipartFile preview,
                         @RequestParam(value = "video", required = false) MultipartFile video) {
        return ApiResponse.ok(storeService.adminUpdate(id, name, free, pricePerDay, sortOrder, active, textColor, scrim,
                image, preview, video), "Item updated");
    }

    @DeleteMapping("/items/{id}")
    public Object delete(@PathVariable String id) {
        storeService.adminDelete(id);
        return ApiResponse.ok(Map.of("deleted", true), "Item deleted");
    }

    private static StoreCategory parse(String category) {
        try {
            return StoreCategory.valueOf(category.trim().toUpperCase());
        } catch (Exception e) {
            throw ApiException.badRequest("category must be FRAME, BUBBLE, BACKGROUND or ENTRY_EFFECT");
        }
    }
}
