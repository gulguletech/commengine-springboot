package com.commengine.controller;

import com.commengine.common.ApiException;
import com.commengine.common.ApiResponse;
import com.commengine.dto.AgencyRequestRequests;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.AgencyRequestService;
import com.commengine.service.CloudinaryService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/** App: "Apply for agency" (any signed-in user). */
@RestController
@RequestMapping("/api/agency/apply")
public class AgencyApplyController {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final AgencyRequestService service;
    private final CloudinaryService cloudinary;

    public AgencyApplyController(AgencyRequestService service, CloudinaryService cloudinary) {
        this.service = service;
        this.cloudinary = cloudinary;
    }

    /** { request: null | {status, agencyName, rejectReason, ...} } */
    @GetMapping("/mine")
    public Object mine(@CurrentUser User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("request", service.mine(user));
        return ApiResponse.ok(data);
    }

    /** Aadhaar front / back / selfie: upload first, then send the links with the form. */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object upload(@CurrentUser User user, @RequestParam("file") MultipartFile file) {
        String type = file.getContentType() == null ? "" : file.getContentType();
        if (!(type.equals("image/png") || type.equals("image/jpeg") || type.equals("image/webp"))) throw ApiException.unprocessable("Use a PNG, JPG or WebP image");
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw ApiException.unprocessable("The image must be under 5 MB");
        CloudinaryService.MediaResult up = cloudinary.uploadMedia(file, "commengine/agency-requests", "image");
        return ApiResponse.ok(Map.of("url", up.url()));
    }

    @PostMapping
    public Object submit(@CurrentUser User user, @Valid @RequestBody AgencyRequestRequests.Submit body) {
        return ApiResponse.ok(service.submit(user, body), "Request sent");
    }
}
