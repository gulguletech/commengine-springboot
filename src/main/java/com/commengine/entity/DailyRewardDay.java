package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** What the admin set for day 1..7 of the daily reward. Common to every user. */
@Entity
@Table(name = "daily_reward_days")
@Getter
@Setter
@NoArgsConstructor
public class DailyRewardDay {

    /** 1..7 */
    @Id
    @Column(name = "day_number")
    private int day;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DailyRewardType type = DailyRewardType.NONE;

    /** COINS only. */
    @Column(nullable = false)
    private long coins = 0;

    /** ITEM only: the store item given. */
    @Column(name = "item_id", length = 64)
    private String itemId;

    /** ITEM only: how many days of the item are added (on top of any days the user already has). */
    @Column(name = "item_days", nullable = false)
    private int itemDays = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
