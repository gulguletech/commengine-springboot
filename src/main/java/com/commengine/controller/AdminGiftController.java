package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.service.GiftService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Admin panel: manage the gift shelf. Covered by AdminAuthInterceptor (/api/admin/**).
 * Create / update are multipart so the image and the optional short video upload in one request.
 */
@RestController
@RequestMapping("/api/admin/gifts")
public class AdminGiftController {

    private final GiftService giftService;

    public AdminGiftController(GiftService giftService) {
        this.giftService = giftService;
    }

    @GetMapping
    public Object list() {
        return ApiResponse.ok(Map.of("items", giftService.adminList()));
    }

    /** multipart: name, price, image (required), video (optional, MP4, max 5s), sortOrder (optional) */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object create(@RequestParam("name") String name,
                         @RequestParam("price") long price,
                         @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
                         @RequestParam("image") MultipartFile image,
                         @RequestParam(value = "video", required = false) MultipartFile video) {
        return ApiResponse.ok(giftService.adminCreate(name, price, sortOrder, image, video), "Gift added");
    }

    /** multipart, every part optional: name, price, sortOrder, active, image, video, removeVideo=true */
    @PostMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object update(@PathVariable String id,
                         @RequestParam(value = "name", required = false) String name,
                         @RequestParam(value = "price", required = false) Long price,
                         @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
                         @RequestParam(value = "active", required = false) Boolean active,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         @RequestParam(value = "video", required = false) MultipartFile video,
                         @RequestParam(value = "removeVideo", defaultValue = "false") boolean removeVideo) {
        return ApiResponse.ok(giftService.adminUpdate(id, name, price, sortOrder, active, image, video, removeVideo), "Gift updated");
    }

    @DeleteMapping("/{id}")
    public Object delete(@PathVariable String id) {
        giftService.adminDelete(id);
        return ApiResponse.ok(Map.of("deleted", true), "Gift deleted");
    }
}
