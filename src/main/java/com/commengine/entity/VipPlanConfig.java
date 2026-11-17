package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** What the admin charged for one VIP length (1, 7, 30, 180 or 365 days). */
@Entity
@Table(name = "vip_plans")
@Getter
@Setter
@NoArgsConstructor
public class VipPlanConfig {

    @Id
    @Column(name = "plan_days")
    private int days;

    /** Price in coins. */
    @Column(name = "price_coins", nullable = false)
    private long priceCoins;

    /** Switched-off plans are not shown in the app. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
