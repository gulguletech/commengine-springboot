package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.service.CloudinaryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final CloudinaryService cloudinaryService;

    public UploadController(CloudinaryService cloudinaryService) {
        this.cloudinaryService = cloudinaryService;
    }

    // POST /api/upload  (multipart, field: file)
    @PostMapping
    public Object uploadFile(@RequestParam("file") MultipartFile file) {
        CloudinaryService.UploadResult result = cloudinaryService.upload(file);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", result.url());
        data.put("publicId", result.publicId());
        data.put("resourceType", result.resourceType());
        return ApiResponse.ok(data, "File uploaded");
    }
}
