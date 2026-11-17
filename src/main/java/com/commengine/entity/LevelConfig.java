package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * What the admin set for level 1..10: how much gift value a user must have received to reach it,
 * and the reward they can collect there. Common to every user.
 */
@Entity
@Table(name = "level_configs")
@Getter
@Setter
@NoArgsConstructor
public class LevelConfig {

    /** 1..10 */
    @Id
    @Column(name = "level_number")
    private int level;

    /** Total value (in coins) of gifts a user must have received to reach this level. */
    @Column(name = "required_coins", nullable = false)
    private long requiredCoins = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", nullable = false, length = 10)
    private DailyRewardType type = DailyRewardType.NONE;

    /** COINS only. */
    @Column(nullable = false)
    private long coins = 0;

    /** ITEM only: the store item given. */
    @Column(name = "item_id", length = 64)
    private String itemId;

    /** ITEM only: how many days of the item are added. */
    @Column(name = "item_days", nullable = false)
    private int itemDays = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
