package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A purchasable coin pack. Managed from the admin panel (Coin Packs page). */
@Entity
@Table(name = "coin_packs")
@Getter
@Setter
@NoArgsConstructor
public class CoinPack {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Coins the user receives. */
    @Column(nullable = false)
    private long coins;

    /** Price in whole INR rupees. */
    @Column(name = "price_inr", nullable = false)
    private long priceInr;

    /** Inactive packs are hidden from the app but kept in the admin list. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
