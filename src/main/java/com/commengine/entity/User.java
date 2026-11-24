package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Short 7-digit ID people can search and share (the long `id` stays internal). */
    @Column(name = "public_id", unique = true, length = 12)
    private String publicId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false)
    private Integer age;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(length = 300)
    private String bio;

    /** One of Catalog.LANGUAGES. Null for accounts made before languages existed. */
    @Column(length = 30)
    private String language;

    /** Every language the user speaks, comma separated ("Hindi,Telugu"). `language` keeps the first one for older app versions. */
    @Column(name = "languages", length = 300)
    private String languages;

    /** All languages the user speaks (falls back to the old single `language`). */
    public java.util.List<String> languageList() {
        return com.commengine.util.Catalog.languageList(languages, language);
    }

    /** Sets the full list; `language` becomes the first entry. */
    public void applyLanguages(java.util.List<String> list) {
        this.languages = com.commengine.util.Catalog.joinLanguages(list);
        this.language = list == null || list.isEmpty() ? null : list.get(0);
    }

    /** VIP membership ends at this moment. null / in the past = not a VIP member. */
    @Column(name = "vip_expires_at")
    private java.time.Instant vipExpiresAt;

    /** The picture a VIP member uploaded to use as a room background. */
    @Column(name = "custom_bg_url", length = 600)
    private String customBgUrl;

    @Column(name = "custom_bg_public_id", length = 300)
    private String customBgPublicId;

    public boolean isVip() {
        return vipExpiresAt != null && vipExpiresAt.isAfter(java.time.Instant.now());
    }

    private String avatarUrl;

    @Column(nullable = false)
    private boolean isVerified = false;

    @Column(nullable = false)
    private boolean isOnline = false;

    @Column(nullable = false)
    private Instant lastSeen = Instant.now();

    /** Set from the admin panel. A banned user cannot log in, call the API, or connect to sockets. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean banned = false;

    /** Official super admin (set from the admin panel). A super admin can never also be in an agency. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean superAdmin = false;

    private Instant bannedAt;

    @Column(length = 200)
    private String banReason;

    // ---- equipped store items (denormalised so DtoMapper can attach them to every user payload without a DB call) ----

    @Column(name = "frame_item_id") private String frameItemId;
    @Column(name = "frame_url", length = 600) private String frameUrl;
    @Column(name = "frame_expires_at") private Instant frameExpiresAt;

    @Column(name = "bubble_item_id") private String bubbleItemId;
    @Column(name = "bubble_url", length = 600) private String bubbleUrl;
    @Column(name = "bubble_text_color", length = 9) private String bubbleTextColor;
    @Column(name = "bubble_expires_at") private Instant bubbleExpiresAt;

    @Column(name = "entry_item_id") private String entryItemId;
    @Column(name = "entry_name", length = 60) private String entryName;
    @Column(name = "entry_video_url", length = 600) private String entryVideoUrl;
    @Column(name = "entry_expires_at") private Instant entryExpiresAt;

    /** null expiry = a free item that never runs out. */
    private static boolean live(Instant expiresAt) {
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }

    public String activeFrameUrl() {
        return frameUrl != null && live(frameExpiresAt) ? frameUrl : null;
    }

    public boolean hasActiveBubble() {
        return bubbleUrl != null && live(bubbleExpiresAt);
    }

    public boolean hasActiveEntryEffect() {
        return entryVideoUrl != null && live(entryExpiresAt);
    }

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
