package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One thing on the store shelf. Managed from the admin panel.
 * Priced PER DAY in coins; a user picks how many days when buying. A free item costs nothing and is usable by everyone.
 */
@Entity
@Table(name = "store_items", indexes = @Index(name = "idx_store_items_category", columnList = "category, active"))
@Getter
@Setter
@NoArgsConstructor
public class StoreItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoreCategory category;

    @Column(nullable = false, length = 60)
    private String name;

    /** Free items are usable by every user without buying. */
    @Column(nullable = false)
    private boolean free = false;

    /** Coins per day. 0 when free. */
    @Column(name = "price_per_day", nullable = false)
    private long pricePerDay = 0;

    /** Main artwork: frame PNG, bubble PNG, background image. For ENTRY_EFFECT this is the small preview image. */
    @Column(name = "image_url", length = 600)
    private String imageUrl;

    @Column(name = "image_public_id", length = 300)
    private String imagePublicId;

    /** ENTRY_EFFECT only: the MP4 played when the user enters a room. */
    @Column(name = "video_url", length = 600)
    private String videoUrl;

    @Column(name = "video_public_id", length = 300)
    private String videoPublicId;

    /** BUBBLE only: colour of the message text drawn on the bubble, e.g. #FFFFFF. */
    @Column(name = "text_color", length = 9)
    private String textColor;

    /** BACKGROUND only: 0..0.8 darkness drawn over the picture so white UI stays readable. */
    @Column(name = "scrim")
    private Double scrim;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
