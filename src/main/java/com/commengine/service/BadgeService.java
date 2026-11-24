package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.entity.Badge;
import com.commengine.entity.UserBadge;
import com.commengine.repository.BadgeRepository;
import com.commengine.repository.UserBadgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/** Official badges: made and edited from the admin panel, given by super admins, shown on profiles. */
@Service
public class BadgeService {

    private static final long MAX_IMAGE_BYTES = 3L * 1024 * 1024;

    private final BadgeRepository badges;
    private final UserBadgeRepository userBadges;
    private final CloudinaryService cloudinary;

    public BadgeService(BadgeRepository badges, UserBadgeRepository userBadges, CloudinaryService cloudinary) {
        this.badges = badges;
        this.userBadges = userBadges;
        this.cloudinary = cloudinary;
    }

    private static Map<String, Object> row(Badge b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("name", b.getName());
        m.put("imageUrl", b.getImageUrl());
        m.put("active", b.isActive());
        m.put("sortOrder", b.getSortOrder());
        m.put("createdAt", b.getCreatedAt());
        return m;
    }

    private static String cleanName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.length() < 2 || n.length() > 40) throw ApiException.unprocessable("Badge name must be 2 to 40 characters");
        return n;
    }

    private void attachImage(Badge b, MultipartFile image) {
        String type = image.getContentType() == null ? "" : image.getContentType();
        if (!(type.equals("image/png") || type.equals("image/jpeg") || type.equals("image/webp"))) {
            throw ApiException.unprocessable("Badge image must be PNG, JPG or WebP");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) throw ApiException.unprocessable("Badge image must be under 3 MB");
        CloudinaryService.MediaResult up = cloudinary.uploadMedia(image, "commengine/badges", "image");
        b.setImageUrl(up.url());
        b.setImagePublicId(up.publicId());
    }

    // ---------------------------------------------------------------- admin panel

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminList() {
        return badges.findAllByOrderBySortOrderAscCreatedAtAsc().stream().map(BadgeService::row).toList();
    }

    @Transactional
    public Map<String, Object> adminCreate(String name, Integer sortOrder, MultipartFile image) {
        String clean = cleanName(name);
        if (image == null || image.isEmpty()) throw ApiException.unprocessable("Badge image is required");
        Badge b = new Badge();
        b.setName(clean);
        b.setSortOrder(sortOrder == null ? 0 : sortOrder);
        attachImage(b, image);
        return row(badges.save(b));
    }

    @Transactional
    public Map<String, Object> adminUpdate(String id, String name, Integer sortOrder, Boolean active, MultipartFile image) {
        Badge b = badges.findById(id).orElseThrow(() -> ApiException.notFound("Badge not found"));
        if (name != null) b.setName(cleanName(name));
        if (sortOrder != null) b.setSortOrder(sortOrder);
        if (active != null) b.setActive(active);
        if (image != null && !image.isEmpty()) {
            String old = b.getImagePublicId();
            attachImage(b, image);
            cloudinary.delete(old, "image");
        }
        return row(badges.save(b));
    }

    @Transactional
    public void adminDelete(String id) {
        Badge b = badges.findById(id).orElseThrow(() -> ApiException.notFound("Badge not found"));
        userBadges.deleteByBadgeId(id);
        badges.delete(b);
        cloudinary.delete(b.getImagePublicId(), "image");
    }

    // ---------------------------------------------------------------- app

    /** Active badges a super admin can pick from. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> giveable() {
        return badges.findByActiveTrueOrderBySortOrderAscCreatedAtAsc().stream().map(BadgeService::row).toList();
    }

    @Transactional(readOnly = true)
    public Badge require(String id) {
        return badges.findById(id).orElseThrow(() -> ApiException.notFound("Badge not found"));
    }

    /** Gives (or renews) a badge. days = null means permanent. Returns when it ends (null = never). */
    @Transactional
    public java.time.Instant give(String userId, Badge badge, Integer days) {
        UserBadge ub = userBadges.findByUserIdAndBadgeId(userId, badge.getId()).orElseGet(() -> {
            UserBadge n = new UserBadge();
            n.setUserId(userId);
            n.setBadgeId(badge.getId());
            return n;
        });
        if (days == null) {
            ub.setExpiresAt(null);
        } else {
            java.time.Instant now = java.time.Instant.now();
            java.time.Instant base = ub.getExpiresAt() != null && ub.getExpiresAt().isAfter(now) ? ub.getExpiresAt() : now;
            // renewing a permanent badge with a number of days would shorten it: keep it permanent
            ub.setExpiresAt(ub.getId() != null && ub.getExpiresAt() == null ? null : base.plus(days, java.time.temporal.ChronoUnit.DAYS));
        }
        userBadges.save(ub);
        return ub.getExpiresAt();
    }

    @Transactional
    public void take(String userId, String badgeId) {
        userBadges.findByUserIdAndBadgeId(userId, badgeId).ifPresent(userBadges::delete);
    }

    /** What a profile shows: the user's badges that are active and not expired. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> activeOf(String userId) {
        List<UserBadge> mine = userBadges.findByUserId(userId).stream().filter(UserBadge::isActive).toList();
        if (mine.isEmpty()) return List.of();
        Map<String, Badge> byId = new HashMap<>();
        badges.findAllById(mine.stream().map(UserBadge::getBadgeId).toList()).forEach(b -> byId.put(b.getId(), b));
        List<Map<String, Object>> out = new ArrayList<>();
        mine.stream()
                .map(ub -> byId.get(ub.getBadgeId()))
                .filter(b -> b != null && b.isActive())
                .sorted(Comparator.comparingInt(Badge::getSortOrder))
                .forEach(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", b.getId());
                    m.put("name", b.getName());
                    m.put("imageUrl", b.getImageUrl());
                    out.add(m);
                });
        return out;
    }
}
