package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A gift on the shelf. Managed from the admin panel (Gifts page).
 * imageUrl is the gift picture; videoUrl is an optional (max 5s) clip played in the room when the gift is sent.
 * Seeded default gifts have no image and use `emoji` instead.
 */
@Entity
@Table(name = "gifts")
@Getter
@Setter
@NoArgsConstructor
public class Gift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 60)
    private String name;

    /** Price in coins. */
    @Column(nullable = false)
    private long price;

    @Column(name = "image_url", length = 600)
    private String imageUrl;

    @Column(name = "image_public_id", length = 300)
    private String imagePublicId;

    @Column(name = "video_url", length = 600)
    private String videoUrl;

    @Column(name = "video_public_id", length = 300)
    private String videoPublicId;

    /** How the clip plays in the room: MINI (small, centred; the default) or FULLSCREEN (about 95% of the screen). */
    @Column(name = "video_display", length = 12, columnDefinition = "varchar(12) default 'MINI'")
    private String videoDisplay = "MINI";

    /** Fallback for seeded gifts that have no uploaded image. */
    @Column(length = 16)
    private String emoji;

    /** Inactive gifts are hidden from the app but kept in the admin list. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
