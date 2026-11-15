package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** Where one user is in the 7-day reward: the last day they collected and which day of the cycle it was. */
@Entity
@Table(name = "daily_reward_claims")
@Getter
@Setter
@NoArgsConstructor
public class DailyRewardClaim {

    @Id
    @Column(name = "user_id")
    private String userId;

    /** Calendar day (India time) of the last collection. */
    @Column(name = "last_claim_date", nullable = false)
    private LocalDate lastClaimDate;

    /** Which day of the 7 was collected last (1..7). */
    @Column(name = "streak_day", nullable = false)
    private int streakDay;

    @Column(name = "total_claims", nullable = false)
    private int totalClaims = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
