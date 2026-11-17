package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.entity.EventBanner;
import com.commengine.repository.EventBannerRepository;
import com.commengine.util.BannerImageValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Event banners on Home. The admin uploads pictures of one exact size; the app shows the active ones in order. */
@Service
public class EventBannerService {

    private static final int MAX_BANNERS = 20;

    private final EventBannerRepository repo;
    private final CloudinaryService cloudinary;

    public EventBannerService(EventBannerRepository repo, CloudinaryService cloudinary) {
        this.repo = repo;
        this.cloudinary = cloudinary;
    }

    // ------------------------------------------------------------------ app side

    @Transactional(readOnly = true)
    public List<Map<String, Object>> active() {
        return repo.findByActiveTrueOrderBySortOrderAscCreatedAtDesc().stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("imageUrl", b.getImageUrl());
            return m;
        }).toList();
    }

    // ------------------------------------------------------------------ admin side

    public Map<String, Object> spec() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("width", BannerImageValidator.WIDTH);
        m.put("height", BannerImageValidator.HEIGHT);
        m.put("maxBytes", BannerImageValidator.MAX_BYTES);
        m.put("format", "PNG or JPG");
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminList() {
        return repo.findAllByOrderBySortOrderAscCreatedAtDesc().stream().map(EventBannerService::adminJson).toList();
    }

    private static Map<String, Object> adminJson(EventBanner b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("title", b.getTitle());
        m.put("imageUrl", b.getImageUrl());
        m.put("active", b.isActive());
        m.put("sortOrder", b.getSortOrder());
        m.put("createdAt", b.getCreatedAt());
        return m;
    }

    private static byte[] checked(MultipartFile image) {
        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException e) {
            throw ApiException.unprocessable("Could not read the uploaded image");
        }
        try {
            BannerImageValidator.validate(bytes);
        } catch (IllegalArgumentException e) {
            throw ApiException.unprocessable(e.getMessage());
        }
        return bytes;
    }

    private static String cleanTitle(String t) {
        if (t == null) return null;
        String s = t.trim();
        if (s.length() > 80) throw ApiException.unprocessable("Title can be at most 80 characters");
        return s.isEmpty() ? null : s;
    }

    @Transactional
    public Map<String, Object> adminCreate(String title, Integer sortOrder, MultipartFile image) {
        if (image == null || image.isEmpty()) throw ApiException.unprocessable("Banner image is required");
        if (repo.count() >= MAX_BANNERS) throw ApiException.unprocessable("You can have at most " + MAX_BANNERS + " banners. Remove one first.");
        checked(image); // validate BEFORE uploading so a rejected file leaves nothing behind

        CloudinaryService.MediaResult up = cloudinary.uploadMedia(image, "commengine/banners", "image");
        EventBanner b = new EventBanner();
        b.setTitle(cleanTitle(title));
        b.setSortOrder(sortOrder == null ? 0 : sortOrder);
        b.setImageUrl(up.url());
        b.setImagePublicId(up.publicId());
        repo.save(b);
        return adminJson(b);
    }

    @Transactional
    public Map<String, Object> adminUpdate(String id, String title, Integer sortOrder, Boolean active, MultipartFile image) {
        EventBanner b = repo.findById(id).orElseThrow(() -> ApiException.notFound("Banner not found"));
        if (title != null) b.setTitle(cleanTitle(title));
        if (sortOrder != null) b.setSortOrder(sortOrder);
        if (active != null) b.setActive(active);
        if (image != null && !image.isEmpty()) {
            checked(image);
            String oldId = b.getImagePublicId();
            CloudinaryService.MediaResult up = cloudinary.uploadMedia(image, "commengine/banners", "image");
            b.setImageUrl(up.url());
            b.setImagePublicId(up.publicId());
            cloudinary.delete(oldId, "image");
        }
        b.setUpdatedAt(Instant.now());
        repo.save(b);
        return adminJson(b);
    }

    @Transactional
    public void adminDelete(String id) {
        EventBanner b = repo.findById(id).orElseThrow(() -> ApiException.notFound("Banner not found"));
        repo.delete(b);
        cloudinary.delete(b.getImagePublicId(), "image");
    }
}
