package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** An event banner shown at the top of Home (above Ranking / Games). Managed from the admin panel. */
@Entity
@Table(name = "event_banners")
@Getter
@Setter
@NoArgsConstructor
public class EventBanner {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Only for the admin's own reference (not shown in the app). */
    @Column(length = 80)
    private String title;

    @Column(name = "image_url", nullable = false, length = 600)
    private String imageUrl;

    @Column(name = "image_public_id", length = 300)
    private String imagePublicId;

    /** Inactive banners are hidden from the app but kept in the admin list. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
